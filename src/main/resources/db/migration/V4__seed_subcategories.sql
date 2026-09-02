insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('SUPERMERCADO', 'LEGUMES_E_FRUTAS', 'Legumes e Frutas', 1),
    ('SUPERMERCADO', 'CARNE', 'Carne', 2),
    ('SUPERMERCADO', 'PEIXE_E_MARISCO', 'Peixe e Marisco', 3),
    ('SUPERMERCADO', 'LATICINIOS', 'Laticínios', 4),
    ('SUPERMERCADO', 'PADARIA', 'Padaria', 5),
    ('SUPERMERCADO', 'BEBIDAS', 'Bebidas', 6),
    ('SUPERMERCADO', 'LIMPEZA', 'Limpeza', 7),
    ('SUPERMERCADO', 'MERCEARIA', 'Mercearia', 8)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('MODA', 'ROUPA_MASCULINA', 'Roupa Masculina', 1),
    ('MODA', 'ROUPA_FEMININA', 'Roupa Feminina', 2),
    ('MODA', 'ROUPA_INFANTIL', 'Roupa Infantil', 3),
    ('MODA', 'CALCADO', 'Calçado', 4),
    ('MODA', 'ACESSORIOS', 'Acessórios', 5)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('ELETRONICA', 'TELEMOVEIS', 'Telemóveis', 1),
    ('ELETRONICA', 'COMPUTADORES', 'Computadores', 2),
    ('ELETRONICA', 'ELETRODOMESTICOS', 'Eletrodomésticos', 3),
    ('ELETRONICA', 'ACESSORIOS_ELETRONICA', 'Acessórios', 4)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('RESTAURANTE', 'PRATOS_PRINCIPAIS', 'Pratos Principais', 1),
    ('RESTAURANTE', 'ENTRADAS', 'Entradas', 2),
    ('RESTAURANTE', 'SOBREMESAS', 'Sobremesas', 3),
    ('RESTAURANTE', 'BEBIDAS_RESTAURANTE', 'Bebidas', 4)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('FARMACIA', 'MEDICAMENTOS', 'Medicamentos', 1),
    ('FARMACIA', 'HIGIENE', 'Higiene', 2),
    ('FARMACIA', 'BEM_ESTAR', 'Bem-estar', 3)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('BELEZA', 'MAQUILHAGEM', 'Maquilhagem', 1),
    ('BELEZA', 'CUIDADOS_DE_PELE', 'Cuidados de Pele', 2),
    ('BELEZA', 'CABELO', 'Cabelo', 3),
    ('BELEZA', 'PERFUMES', 'Perfumes', 4)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, v.code, v.name, v.sort_order, true
from categories, (values
    ('CASA_E_JARDIM', 'MOVEIS', 'Móveis', 1),
    ('CASA_E_JARDIM', 'DECORACAO', 'Decoração', 2),
    ('CASA_E_JARDIM', 'JARDIM', 'Jardim', 3),
    ('CASA_E_JARDIM', 'FERRAMENTAS', 'Ferramentas', 4)
) as v(category_code, code, name, sort_order)
where categories.code = v.category_code;

insert into subcategories (category_id, code, name, sort_order, active)
select id, 'GERAL', 'Geral', 1, true
from categories where code = 'OUTROS';
