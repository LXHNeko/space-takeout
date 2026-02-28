package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 订单提交
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 处理各种业务异常（地址簿为空，购物车数据为空）
        // 查询当前地址簿数据
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null) { // 抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList == null || shoppingCartList.isEmpty()){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID); // 未付款
        orders.setStatus(Orders.PENDING_PAYMENT); // 待付款
        orders.setNumber(String.valueOf(System.currentTimeMillis())); // 用时间戳当做订单号
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        // 向订单明细表插入N条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail(); // 订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 设置当前订单明细关联的订单ID
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 历史订单查询
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(OrdersPageQueryDTO ordersPageQueryDTO) {

        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> ordersPage = orderMapper.page(ordersPageQueryDTO);

        //封装VO
        List<OrderVO> voList = new ArrayList<>();
        List<OrderDetail> orderDetails;
        if(ordersPage != null && ordersPage.getTotal() > 0){
            for (Orders orders : ordersPage) {
                orderDetails = orderDetailMapper.getByOrderId(orders.getId());
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                voList.add(orderVO);
            }
        }

        return new PageResult(ordersPage.getTotal(), voList);
    }

    /**
     * 取消订单
     * @param id
     */
    @Override
    public void cancel(Long id) {
        Orders currentOrder = orderMapper.getById(id);

        // 先检查有没有这个订单
        if(currentOrder == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Integer status = currentOrder.getStatus();

        // 商家已接单状态下，用户取消订单需电话沟通商家
        if(Objects.equals(status, Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_CONFIRMED);
        }

        // 派送中状态下，用户取消订单需电话沟通商家
        if(Objects.equals(status, Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_DELIVER_ON_THE_WAY);
        }

        // 如果是已完成的订单，依然不能直接取消
        if(Objects.equals(status, Orders.COMPLETED)){
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_COMPLETED);
        }

        // 防止对已取消的订单重复操作
        if(Objects.equals(status, Orders.CANCELLED)){
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_CANCELLED);
        }

        // 其他意料外的异常状态，如99
        if(!Objects.equals(status, Orders.PENDING_PAYMENT) && !Objects.equals(status, Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 待支付和待接单状态下，用户可直接取消订单
        // 构建订单对象准备操作数据库进行订单修改操作
        // 取消订单后需要将订单状态修改为“已取消”
        Orders order = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason("用户取消")
                .build();

        // 如果在待接单状态下取消订单，需要给用户退款
        if(Objects.equals(status, Orders.TO_BE_CONFIRMED)){
            try {
                // 个人测试环境，暂时将实际退款函数调用注释掉
                // weChatPayUtil.refund(currentOrder.getNumber(), currentOrder.getNumber(), currentOrder.getAmount(), currentOrder.getAmount());
                // 更新订单的支付状态
                order.setPayStatus(Orders.REFUND);
            } catch (Exception e){
                throw new OrderBusinessException("订单取消失败，退款异常");
            }
        }
        orderMapper.update(order);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void orderAgain(Long id) {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        // 再来一单就是将原订单中的商品重新加入到购物车中
        // 先把订单详情列表查出来
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);
        if(orderDetails == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);

        // 再把详情一个个的封装到购物车并且操作数据库
        for (OrderDetail orderDetail : orderDetails) {
            // 必须在循环内部new对象，保证每件商品都是独立的
            ShoppingCart shoppingCart = new ShoppingCart();

            // 直接把 OrderDetail 里的同名属性（名字、图片、价格、数量、口味等）全部抄到购物车里
            // 注意：一定要忽略 "id" 字段，因为订单详情的 id 和购物车的 id 是两码事
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");

            // 补充购物车特有的属性
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            // 可以优化成批量插入
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        Integer confirmed = orderMapper.getCountByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.getCountByStatus(Orders.DELIVERY_IN_PROGRESS);
        Integer toBeConfirmed = orderMapper.getCountByStatus(Orders.TO_BE_CONFIRMED);
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        return orderStatisticsVO;
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO orderDetail(Long id) {
        //根据订单id查询订单
        Orders order = orderMapper.getById(id);

        //根据订单id查询可能包含多个的订单详情
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        //打包vo
        OrderVO orderVO = new OrderVO();
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        BeanUtils.copyProperties(order, orderVO);
        orderVO.setOrderDetailList(orderDetails);

        //返回
        return orderVO;
    }
}
