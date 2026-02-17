package com.sky.service.impl;

import com.sky.dto.SetmealDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.SetmealService;
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

        //用Mapper传回来的setmeal的id值赋值中间表实体类的setmealId
        setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmeal.getId()));

        //保存套餐菜品至数据库
        setmealDishMapper.insertBatch(setmealDishes);
    }

}
