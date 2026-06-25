package com.example.price_tracker.service;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.vo.ProductPageVo;
import com.example.price_tracker.vo.UserVo;

public interface AdminService {

    PageResult<UserVo> pageUsers(Long pageNum, Long pageSize, String keyword);

    PageResult<ProductPageVo> pageProducts(Long pageNum, Long pageSize, String keyword);

    void updateProductStatus(Long productId, Integer status);
}
