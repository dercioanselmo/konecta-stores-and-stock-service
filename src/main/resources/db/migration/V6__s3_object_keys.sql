-- Photos/logo/cover now live in a private S3 bucket, accessed exclusively
-- via presigned URLs generated on read. These columns store the S3 object
-- key, not a directly usable URL — rename to make that unambiguous.

alter table product_images rename column url to object_key;

alter table stores rename column logo_url to logo_key;
alter table stores rename column cover_url to cover_key;
