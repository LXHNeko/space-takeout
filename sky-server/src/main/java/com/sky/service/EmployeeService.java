package com.sky.service;

import com.github.pagehelper.Page;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);
    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    PageResult page(EmployeePageQueryDTO employeePageQueryDTO);
    /**
     * 设置员工账号状态
     * @param status
     * @param id
     * @return
     */
    void setStatus(Integer status, Long id);

    /**
     * 根据ID查询员工
     * @param id
     * @return
     */
    Employee getEmployeeById(Long id);

    /**
     * 更新员工
     * @param employeeDTO
     * @return
     */
    void update(EmployeeDTO employeeDTO);
}
