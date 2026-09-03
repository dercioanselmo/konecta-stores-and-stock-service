-- Photo upload is now supported (local disk storage for this phase), so
-- product photos become real rows (stable id, needed for delete/set-primary)
-- instead of a bare list of URL strings.

alter table products drop column image_urls;
alter table products drop column primary_image_url;

create table product_images (
    id         uuid primary key default gen_random_uuid(),
    product_id uuid not null references products (id) on delete cascade,
    url        varchar(500) not null,
    is_primary boolean not null default false,
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

create index idx_product_images_product_id on product_images (product_id);
