package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.admin.mapper.AdminPermissionMapper;
import com.shop.model.admin.dto.AdminPermissionCreateDTO;
import com.shop.model.admin.entity.AdminPermission;
import com.shop.model.admin.vo.AdminPermissionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminPermissionServiceImpl 权限服务实现类的单元测试
 * <p>
 * 验证权限树构建、权限CRUD等核心业务逻辑：
 * 1. getPermissionTree - 树构建（根节点parentId=0/null + 递归children）
 * 2. deletePermission - 有子权限不可删
 * 3. getPermissionById / createPermission / updatePermission
 * </p>
 * <p>
 * 小白理解：权限管理是后台菜单和按钮权限的核心，权限分三种类型：目录、菜单、按钮。
 * 通过父子关系（parentId）构建树形菜单结构，前端可以递归渲染成菜单树。
 * 我们用 Mock 把数据库 Mapper 假装出来，测试不依赖真实数据库。
 * </p>
 */
@DisplayName("AdminPermissionServiceImpl 权限服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPermissionServiceImplTest {

    /** 假装操作 admin_permission 表的 Mapper */
    @Mock
    private AdminPermissionMapper adminPermissionMapper;

    /** 被测服务，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private AdminPermissionServiceImpl adminPermissionService;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：源码里用到了 .orderByAsc(AdminPermission::getSort) 这种写法，
     * MyBatis-Plus 需要知道 AdminPermission::getSort 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AdminPermission.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个权限实体
     *
     * @param id       权限ID
     * @param parentId 父级权限ID，0表示顶级
     * @param name     权限名称
     * @return 构造好的 AdminPermission
     */
    private AdminPermission buildPermission(Long id, Long parentId, String name) {
        AdminPermission permission = new AdminPermission();
        permission.setId(id);
        permission.setParentId(parentId);
        permission.setName(name);
        permission.setType(1);
        permission.setSort(1);
        permission.setStatus(1);
        return permission;
    }

    /**
     * 构造一个新增权限DTO
     *
     * @param name     权限名称
     * @param parentId 父级权限ID
     * @return 构造好的 AdminPermissionCreateDTO
     */
    private AdminPermissionCreateDTO buildCreateDTO(String name, Long parentId) {
        AdminPermissionCreateDTO dto = new AdminPermissionCreateDTO();
        dto.setParentId(parentId);
        dto.setName(name);
        dto.setType(1);
        dto.setPermissionKey("system:user:list");
        dto.setPath("/system/user");
        dto.setIcon("user");
        dto.setSort(1);
        return dto;
    }

    // ==================== 1. getPermissionTree 获取权限树 ====================

    @Nested
    @DisplayName("getPermissionTree 获取权限树")
    class GetPermissionTreeTest {

        @Test
        @DisplayName("空列表：返回空列表")
        void emptyList_shouldReturnEmpty() {
            when(adminPermissionMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<AdminPermissionVO> result = adminPermissionService.getPermissionTree();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("有数据：正确构建三级树形结构（目录→菜单→按钮）")
        void withData_shouldBuildTree() {
            // 构造三级权限：目录→菜单→按钮
            AdminPermission root = buildPermission(1L, 0L, "系统管理");
            AdminPermission child = buildPermission(2L, 1L, "用户管理");
            AdminPermission grandchild = buildPermission(3L, 2L, "新增用户");
            when(adminPermissionMapper.selectList(any()))
                .thenReturn(Arrays.asList(root, child, grandchild));

            List<AdminPermissionVO> result = adminPermissionService.getPermissionTree();

            // 验证：返回1个根节点
            assertThat(result).hasSize(1);
            AdminPermissionVO rootVO = result.get(0);
            assertThat(rootVO.getId()).isEqualTo(1L);
            assertThat(rootVO.getName()).isEqualTo("系统管理");

            // 验证：根节点有1个子节点
            assertThat(rootVO.getChildren()).hasSize(1);
            AdminPermissionVO childVO = rootVO.getChildren().get(0);
            assertThat(childVO.getId()).isEqualTo(2L);
            assertThat(childVO.getName()).isEqualTo("用户管理");

            // 验证：子节点有1个孙节点
            assertThat(childVO.getChildren()).hasSize(1);
            AdminPermissionVO grandchildVO = childVO.getChildren().get(0);
            assertThat(grandchildVO.getId()).isEqualTo(3L);
            assertThat(grandchildVO.getName()).isEqualTo("新增用户");
        }

        @Test
        @DisplayName("parentId为null的权限：作为根节点处理")
        void nullParentId_shouldBeRoot() {
            AdminPermission root = buildPermission(1L, null, "系统管理");
            AdminPermission child = buildPermission(2L, 1L, "用户管理");
            when(adminPermissionMapper.selectList(any())).thenReturn(Arrays.asList(root, child));

            List<AdminPermissionVO> result = adminPermissionService.getPermissionTree();

            // 验证：parentId为null的权限被当作根节点
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getChildren()).hasSize(1);
        }
    }

    // ==================== 2. deletePermission 删除权限 ====================

    @Nested
    @DisplayName("deletePermission 删除权限")
    class DeletePermissionTest {

        @Test
        @DisplayName("权限不存在：抛ADMIN_PERMISSION_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminPermissionMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminPermissionService.deletePermission(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PERMISSION_NOT_FOUND.getCode());

            verify(adminPermissionMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("有子权限：抛OPERATION_FAIL异常，不执行删除")
        void hasChildren_shouldThrowException() {
            AdminPermission permission = buildPermission(1L, 0L, "系统管理");
            when(adminPermissionMapper.selectById(1L)).thenReturn(permission);
            // 模拟有1个子权限
            when(adminPermissionMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminPermissionService.deletePermission(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            verify(adminPermissionMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除成功：调用deleteById")
        void success_shouldCallDeleteById() {
            AdminPermission permission = buildPermission(1L, 0L, "系统管理");
            when(adminPermissionMapper.selectById(1L)).thenReturn(permission);
            // 模拟没有子权限
            when(adminPermissionMapper.selectCount(any())).thenReturn(0L);

            adminPermissionService.deletePermission(1L);

            // 验证：调用了deleteById（物理删除）
            verify(adminPermissionMapper).deleteById(1L);
        }
    }

    // ==================== 3. getPermissionById 查询权限详情 ====================

    @Nested
    @DisplayName("getPermissionById 查询权限详情")
    class GetPermissionByIdTest {

        @Test
        @DisplayName("权限不存在：抛ADMIN_PERMISSION_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminPermissionMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminPermissionService.getPermissionById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PERMISSION_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("查询成功：返回VO")
        void found_shouldReturnVO() {
            AdminPermission permission = buildPermission(1L, 0L, "系统管理");
            when(adminPermissionMapper.selectById(1L)).thenReturn(permission);

            AdminPermissionVO vo = adminPermissionService.getPermissionById(1L);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getName()).isEqualTo("系统管理");
            assertThat(vo.getParentId()).isEqualTo(0L);
        }
    }

    // ==================== 4. createPermission 新增权限 ====================

    @Nested
    @DisplayName("createPermission 新增权限")
    class CreatePermissionTest {

        @Test
        @DisplayName("新增成功：parentId和sort为null时默认为0，状态默认为1")
        void success_shouldSetDefaults() {
            AdminPermissionCreateDTO dto = new AdminPermissionCreateDTO();
            dto.setParentId(null); // 不传父级，应该是顶级
            dto.setSort(null);      // 不传排序号，默认0
            dto.setName("系统管理");
            dto.setType(1);
            when(adminPermissionMapper.insert(any(AdminPermission.class))).thenReturn(1);

            adminPermissionService.createPermission(dto);

            // 验证：捕获保存的权限对象，检查默认值
            ArgumentCaptor<AdminPermission> permCaptor = ArgumentCaptor.forClass(AdminPermission.class);
            verify(adminPermissionMapper).insert(permCaptor.capture());
            AdminPermission saved = permCaptor.getValue();
            // parentId为null时默认为0
            assertThat(saved.getParentId()).isEqualTo(0L);
            // sort为null时默认为0
            assertThat(saved.getSort()).isEqualTo(0);
            // 新增权限默认状态为正常(1)
            assertThat(saved.getStatus()).isEqualTo(1);
        }
    }

    // ==================== 5. updatePermission 修改权限 ====================

    @Nested
    @DisplayName("updatePermission 修改权限")
    class UpdatePermissionTest {

        @Test
        @DisplayName("权限不存在：抛ADMIN_PERMISSION_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            AdminPermissionCreateDTO dto = new AdminPermissionCreateDTO();
            dto.setName("新名字");
            when(adminPermissionMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminPermissionService.updatePermission(999L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_PERMISSION_NOT_FOUND.getCode());

            verify(adminPermissionMapper, never()).updateById(any(AdminPermission.class));
        }

        @Test
        @DisplayName("修改成功：更新字段")
        void success_shouldUpdateFields() {
            AdminPermission permission = buildPermission(1L, 0L, "系统管理");
            when(adminPermissionMapper.selectById(1L)).thenReturn(permission);

            AdminPermissionCreateDTO dto = new AdminPermissionCreateDTO();
            dto.setName("新名字");
            dto.setSort(99);

            adminPermissionService.updatePermission(1L, dto);

            // 验证：调用了updateById，字段已更新
            ArgumentCaptor<AdminPermission> permCaptor = ArgumentCaptor.forClass(AdminPermission.class);
            verify(adminPermissionMapper).updateById(permCaptor.capture());
            AdminPermission updated = permCaptor.getValue();
            assertThat(updated.getName()).isEqualTo("新名字");
            assertThat(updated.getSort()).isEqualTo(99);
        }
    }
}
