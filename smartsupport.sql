create table message_log
(
    id              bigint       not null
        primary key,
    message_content text         null,
    exchange        varchar(100) null,
    routing_key     varchar(100) null,
    status          tinyint      null,
    retry_count     int          null,
    create_time     datetime     null,
    update_time     datetime     null
);

create table notification
(
    id         bigint auto_increment
        primary key,
    user_id    bigint                               not null,
    type       varchar(64)                          null,
    content    text                                 null,
    read_flag  tinyint(1) default 0                 null,
    created_at datetime   default CURRENT_TIMESTAMP null
);

create index user_id
    on notification (user_id);

create table outbox_message
(
    id          bigint auto_increment
        primary key,
    event_type  varchar(100)                          not null,
    payload     json                                  not null,
    status      varchar(20) default 'PENDING'         null,
    retry_count int         default 0                 null,
    create_time datetime    default CURRENT_TIMESTAMP null
);

create table ticket
(
    id          bigint auto_increment
        primary key,
    title       varchar(255)                                                                not null,
    description text                                                                        null,
    status      enum ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'WAITING_AGENT', 'RESOLVED', 'CLOSED', 'CANCELLED') default 'NEW'             null,
    priority    enum ('LOW', 'MEDIUM', 'HIGH')                    default 'MEDIUM'          null,
    user_id     bigint                                                                      not null,
    assignee_id bigint                                                                      null,
    origin      enum ('WEB', 'EMAIL', 'API')                      default 'WEB'             null,
    created_at  datetime                                          default CURRENT_TIMESTAMP null,
    updated_at  datetime                                          default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);

create index assignee_id
    on ticket (assignee_id);

create index status
    on ticket (status);

create index user_id
    on ticket (user_id);

create table ticket_attachment
(
    id          bigint auto_increment
        primary key,
    ticket_id   bigint                             not null comment '关联的工单ID',
    file_name   varchar(255)                       not null comment '原始文件名',
    file_url    varchar(500)                       not null comment '存储后的文件访问路径',
    file_type   varchar(50)                        null comment '文件类型，例如 image/png',
    file_size   bigint                             null comment '文件大小（字节）',
    uploader_id bigint                             null comment '上传者ID',
    created_at  datetime default CURRENT_TIMESTAMP null,
    constraint fk_ticket_attachment_ticket
        foreign key (ticket_id) references ticket (id)
            on delete cascade
);

create index ticket_id
    on ticket_attachment (ticket_id);

create table ticket_message
(
    id          bigint auto_increment
        primary key,
    ticket_id   bigint                                                           not null,
    sender_id   bigint                                                           null,
    sender_type enum ('USER', 'AGENT', 'SYSTEM', 'AI') default 'USER'            null,
    content     text                                                             null,
    is_ai       tinyint(1)                             default 0                 null,
    created_at  datetime                               default CURRENT_TIMESTAMP null
);

create index ticket_id
    on ticket_message (ticket_id);

create table ticket_statistics
(
    id               bigint auto_increment
        primary key,
    date             date                               not null,
    new_tickets      int      default 0                 null,
    resolved_tickets int      default 0                 null,
    closed_tickets   int      default 0                 null,
    avg_resolve_time double   default 0                 null,
    created_at       datetime default CURRENT_TIMESTAMP null
);



create table user
(
    id                  bigint auto_increment
        primary key,
    username            varchar(64)                                               not null,
    email               varchar(128)                                              null,
    password_hash       varchar(128)                                              null,
    role                enum ('USER', 'AGENT', 'ADMIN') default 'USER'            null,
    avatar              varchar(255)                                              null,
    created_at          datetime                        default CURRENT_TIMESTAMP null,
    updated_at          datetime                        default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    active_ticket_count int                                                       null,
    state               int                                                       not null,
    constraint username
        unique (username)
);

create table user_auth
(
    id            bigint unsigned auto_increment comment '主键'
        primary key,
    user_id       bigint                             not null comment '关联 sys_user.id',
    identity_type varchar(32)                        not null comment '登录类型（password、github、wechat 等）',
    identifier    varchar(128)                       not null comment '唯一标识（用户名 / 第三方 openid / github_id）',
    credential    varchar(255)                       null comment '凭证（密码 / token / 空）',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_identity_identifier
        unique (identity_type, identifier),
    constraint fk_user_auth_user_id
        foreign key (user_id) references user (id)
            on update cascade on delete cascade
)
    comment '用户认证表（支持多种登录方式）';

create index idx_user_id
    on user_auth (user_id);