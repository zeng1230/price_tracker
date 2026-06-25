package com.example.price_tracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.price_tracker.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            <script>
            SELECT id, username, email, nickname, role, status, created_at, updated_at
            FROM tb_user
            <if test="keyword != null and keyword != ''">
              WHERE username LIKE CONCAT('%', #{keyword}, '%')
                 OR email LIKE CONCAT('%', #{keyword}, '%')
                 OR nickname LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    Page<User> selectAdminPage(Page<User> page, @Param("keyword") String keyword);
}
