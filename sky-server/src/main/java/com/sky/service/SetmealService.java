package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.mapper.SetmealMapper;

import java.util.List;

public interface SetmealService {

    /**
     * 新增套餐
     * @param setmealDTO
     */
    void save(SetmealDTO setmealDTO);
}
