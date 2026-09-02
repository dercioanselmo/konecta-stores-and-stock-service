-- Categories become a two-level taxonomy:
--   Category    = store-level category (e.g. Supermercado, Beleza) — a store may have several.
--   Subcategory = product-level category, scoped to one parent Category (e.g. Legumes under Supermercado).

create table subcategories (
    id         uuid primary key default gen_random_uuid(),
    category_id uuid not null references categories (id) on delete cascade,
    code       varchar(60) not null,
    name       varchar(100) not null,
    sort_order integer not null default 0,
    active     boolean not null default true,
    constraint uk_subcategories_category_code unique (category_id, code)
);

create index idx_subcategories_category_id on subcategories (category_id);

-- Store <-> Category is many-to-many: a store may list under several categories.
create table store_categories (
    id          uuid primary key default gen_random_uuid(),
    store_id    uuid not null references stores (id) on delete cascade,
    category_id uuid not null references categories (id) on delete cascade,
    constraint uk_store_categories_store_category unique (store_id, category_id)
);

create index idx_store_categories_store_id on store_categories (store_id);
create index idx_store_categories_category_id on store_categories (category_id);

alter table stores drop column category;

alter table products drop column category_code;
alter table products add column subcategory_id uuid references subcategories (id);

create index idx_products_subcategory_id on products (subcategory_id);
