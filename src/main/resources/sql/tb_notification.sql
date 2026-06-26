create table tb_notification
(
    id           bigint auto_increment comment '通知ID'
        primary key,
    user_id      bigint                             not null comment '用户ID',
    product_id   bigint                             not null comment '商品ID',
    watchlist_id bigint                             not null comment '关注记录ID',
    event_key    varchar(191)                       null comment 'notification business event key',
    notify_type  varchar(50)                        not null comment '通知类型',
    content      varchar(500)                       not null comment '通知内容',
    is_read      tinyint  default 0                 not null comment '是否已读 0未读 1已读',
    send_status  tinyint  default 0                 not null comment '发送状态 0未发送 1已发送',
    created_at   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    sent_at      datetime                           null comment '发送时间'
)
    comment '通知表';
