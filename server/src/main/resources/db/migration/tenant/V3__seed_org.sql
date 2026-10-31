-- 种子：角色 / 部门 / 员工 / 基础权限（对应《数据库表结构设计.md》§13 与 §16.2）
-- 注：登录账号 users 与其角色绑定由应用启动时初始化（密码需 BCrypt 编码，见 DataInitializer）

INSERT INTO role (code, name, data_scope, description) VALUES
('sys_admin','系统管理员','all','部署与配置'),
('boss','老板/总经理','all','全局经营与关键审批'),
('manager','部门主管','dept','管本部门'),
('finance','财务','all','应收应付与付款审批'),
('hr','人事','all','组织与薪资'),
('sales','销售','customer','负责客户与订单'),
('purchase','采购','dept','采购执行'),
('warehouse','仓库管理员','warehouse','库存管理'),
('employee','普通员工','self','个人待办与档案')
ON CONFLICT (code) DO NOTHING;

INSERT INTO department (name, path, level, sort_no)
SELECT v.name, v.path, v.level::int, v.sort_no::int
FROM (VALUES
    ('总经办','/1',1,1),
    ('销售部','/2',1,2),
    ('采购部','/3',1,3),
    ('财务部','/4',1,4),
    ('人事行政部','/5',1,5),
    ('仓库','/6',1,6)
) AS v(name, path, level, sort_no)
WHERE NOT EXISTS (SELECT 1 FROM department d WHERE d.name = v.name);

INSERT INTO employee (employee_no, real_name, department_id, position, entry_status, status, hire_date)
SELECT v.no, v.name, (SELECT id FROM department WHERE name = v.dept), v.pos, 'regular', 'active', DATE '2021-01-04'
FROM (VALUES
    ('E0001','张总','总经办','总经理'),
    ('E0002','赵财务','财务部','财务经理'),
    ('E0003','陈人事','人事行政部','人事主管'),
    ('E0012','李经理','销售部','销售主管'),
    ('E0021','王销售','销售部','客户经理'),
    ('E0045','小林','销售部','销售专员')
) AS v(no, name, dept, pos)
ON CONFLICT (employee_no) DO NOTHING;

INSERT INTO permission (code, module, action, description) VALUES
('user:view','user','view','查看组织与用户'),
('user:create','user','create','新增部门/员工/账号'),
('user:approve','user','approve','审批'),
('user:export','user','export','导出')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code IN ('sys_admin','boss')
ON CONFLICT DO NOTHING;
