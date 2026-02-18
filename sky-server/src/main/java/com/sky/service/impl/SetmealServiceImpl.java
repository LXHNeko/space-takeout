package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增套餐
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        //将DTO转为实体类
        //准备好套餐实体类和中间表实体类集合
        Setmeal setmeal = new Setmeal();
        List<SetmealDish> setmealDishes = new ArrayList<>();

        //使用方法接收到的setmealDTOs参数转换成实体类
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealDishes = setmealDTO.getSetmealDishes();

/*        以下代码顺序必须保持，
        因为必须先执行setmealMapper.insert才能得到返回的id值，
        才能赋值给setmealDishes里每个元素的setmealId*/

        //保存套餐至数据库
        setmealMapper.insert(setmeal);

        if(setmealDishes != null && setmealDishes.size() > 0){
            //用Mapper传回来的setmeal的id值赋值中间表实体类的setmealId
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmeal.getId()));

            //保存套餐菜品至数据库
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.page(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 删除套餐
     * @param ids
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if(ids.isEmpty()){
            throw new DeletionNotAllowedException(MessageConstant.ID_LIST_IS_NULL);
        }

        //起售中的套餐不能删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if(setmeal.getStatus() == StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

        setmealMapper.deleteByIds(ids);
    }

    /**
     * 根据ID查询套餐
     * @param id
     * @return
     */
    @Override
    public SetmealVO getById(Long id) {
        //获取数据
        Setmeal setmeal = setmealMapper.getById(id);
        List<SetmealDish> setmealDishes = setmealDishMapper.getById(id);
        //封装为VO
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        //返回封装好的VO
        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     * @return
     */
    @Override
    @Transactional
    public void updateWithDishes(SetmealDTO setmealDTO) {
        //将DTO的数据封装到 套餐实体类 和 套餐菜品集合
        Setmeal setmeal = new Setmeal();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //操作数据库进行修改操作
        setmealMapper.update(setmeal);
        //套餐菜品用先删后增的方法达到更新覆盖的效果
        setmealDishMapper.deleteBySetmealId(setmeal.getId());
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmeal.getId());
        }
        setmealDishMapper.insertBatch(setmealDishes);
    }

}
