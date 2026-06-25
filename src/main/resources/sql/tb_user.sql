CREATE TABLE tb_user (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
                         username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
                         password VARCHAR(100) NOT NULL COMMENT '密码',
                         email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
                         nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
                         role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色 USER普通用户 ADMIN管理员',
                         status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         CONSTRAINT chk_tb_user_role CHECK (role IN ('USER', 'ADMIN'))
) COMMENT='用户表';
