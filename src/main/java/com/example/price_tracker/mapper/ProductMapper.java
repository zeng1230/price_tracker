package com.example.price_tracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("""
            <script>
            SELECT id, product_name, product_url, platform, current_price, currency, image_url,
                   status, last_checked_at, created_at, updated_at
            FROM tb_product
            <if test="keyword != null and keyword != ''">
              WHERE product_name LIKE CONCAT('%', #{keyword}, '%')
                 OR platform LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    Page<Product> selectAdminPage(Page<Product> page, @Param("keyword") String keyword);

    @Select("""
            SELECT id, product_name, product_url, platform, current_price, currency, image_url,
                   status, last_checked_at, created_at, updated_at
            FROM tb_product
            WHERE id = #{productId}
            """)
    Product selectAdminById(@Param("productId") Long productId);

    @Update("""
            UPDATE tb_product
            SET status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{productId}
            """)
    int updateStatusByAdmin(@Param("productId") Long productId, @Param("status") Integer status);
}
