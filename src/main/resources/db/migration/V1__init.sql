create extension if not exists "pgcrypto";

create table stores (
    id                          uuid primary key default gen_random_uuid(),
    owner_user_id               varchar(64) not null,
    trade_name                  varchar(150) not null,
    legal_name                  varchar(150),
    nuit                        varchar(32),
    email                       varchar(150),
    phone                       varchar(32),
    address_line                varchar(250),
    city                        varchar(60),
    neighborhood                varchar(100),
    latitude                    double precision,
    longitude                   double precision,
    category                    varchar(60),
    description                 varchar(2000),
    status                      varchar(20) not null default 'DRAFT',
    logo_url                    varchar(500),
    cover_url                   varchar(500),
    accepts_pickup              boolean not null default true,
    accepts_delivery            boolean not null default false,
    default_preparation_minutes integer,
    manually_closed             boolean not null default false,
    manually_closed_reason      varchar(250),
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now()
);

create index idx_stores_owner_user_id on stores (owner_user_id);

create table opening_hours (
    id          uuid primary key default gen_random_uuid(),
    store_id    uuid not null references stores (id) on delete cascade,
    day_of_week varchar(10) not null,
    opens_at    time,
    closes_at   time,
    closed      boolean not null default false,
    constraint uk_opening_hours_store_day unique (store_id, day_of_week)
);

create table categories (
    id         uuid primary key default gen_random_uuid(),
    code       varchar(60) not null unique,
    name       varchar(100) not null,
    sort_order integer not null default 0,
    active     boolean not null default true
);

create table products (
    id                  uuid primary key default gen_random_uuid(),
    store_id            uuid not null references stores (id) on delete cascade,
    name                varchar(150) not null,
    description         varchar(2000),
    slug                varchar(180),
    category_code       varchar(60),
    price               numeric(12, 2) not null,
    currency            varchar(3) not null default 'MT',
    status              varchar(20) not null default 'ACTIVE',
    specs               jsonb,
    image_urls          jsonb not null default '[]'::jsonb,
    primary_image_url   varchar(500),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_products_store_id on products (store_id);

create table inventory (
    id                  uuid primary key default gen_random_uuid(),
    product_id          uuid not null unique references products (id) on delete cascade,
    quantity_available  integer not null default 0,
    quantity_reserved   integer not null default 0,
    low_stock_threshold integer not null default 5,
    updated_at          timestamptz not null default now(),
    constraint chk_inventory_non_negative check (quantity_available >= 0 and quantity_reserved >= 0)
);

create table stock_movements (
    id            uuid primary key default gen_random_uuid(),
    product_id    uuid not null references products (id) on delete cascade,
    delta         integer not null,
    reason        varchar(30) not null,
    ref_type      varchar(30),
    ref_id        varchar(64),
    actor_user_id varchar(64),
    created_at    timestamptz not null default now()
);

create index idx_stock_movements_product_id on stock_movements (product_id);
