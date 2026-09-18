package com.shop.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.admin.mapper.AdminDeptMapper;
import com.shop.admin.mapper.AdminUserMapper;
import com.shop.model.admin.dto.AdminDeptCreateDTO;
import com.shop.model.admin.dto.AdminDeptUpdateDTO;
import com.shop.model.admin.entity.AdminDept;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.vo.AdminDeptVO;
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
 * AdminDeptServiceImpl 部门服务实现类的单元测试
 * <p>
 * 验证部门树构建、部门CRUD等核心业务逻辑：
 * 1. getDeptTree - 树构建
 * 2. createDept - 同父部门下名称查重
 * 3. updateDept - 改名时查重（排除自身）
 * 4. deleteDept - 有子部门不可删 / 有用户不可删
 * 5. getDeptById - 查询部门详情
 * </p>
 * <p>
 * 小白理解：部门用来组织管理员，配合角色的数据权限范围控制管理员能看到哪些数据。
 * 通过父子关系（parentId）构建树形部门结构，比如"总公司"下面有"技术部"、"运营部"。
 * 我们用 Mock 把数据库 Mapper 假装出来，测试不依赖真实数据库。
 * </p>
 */
@DisplayName("AdminDeptServiceImpl 部门服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDeptServiceImplTest {

    /** 假装操作 admin_dept 表的 Mapper */
    @Mock
    private AdminDeptMapper adminDeptMapper;

    /** 假装操作 admin_user 表的 Mapper（用来检查部门下是否有管理员） */
    @Mock
    private AdminUserMapper adminUserMapper;

    /** 被测服务，Mockito 会自动把上面的 Mock 注入进去 */
    @InjectMocks
    private AdminDeptServiceImpl adminDeptService;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：源码里用到了 .eq(AdminDept::getParentId, ...) 这种写法，
     * MyBatis-Plus 需要知道 AdminDept::getParentId 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AdminDept.class);
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个部门实体
     *
     * @param id       部门ID
     * @param parentId 父级部门ID，0表示顶级
     * @param name     部门名称
     * @return 构造好的 AdminDept
     */
    private AdminDept buildDept(Long id, Long parentId, String name) {
        AdminDept dept = new AdminDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setName(name);
        dept.setSort(1);
        dept.setLeader("负责人");
        dept.setStatus(1);
        return dept;
    }

    /**
     * 构造一个新增部门DTO
     *
     * @param name     部门名称
     * @param parentId 父级部门ID
     * @return 构造好的 AdminDeptCreateDTO
     */
    private AdminDeptCreateDTO buildCreateDTO(String name, Long parentId) {
        AdminDeptCreateDTO dto = new AdminDeptCreateDTO();
        dto.setParentId(parentId);
        dto.setName(name);
        dto.setSort(1);
        dto.setLeader("负责人");
        return dto;
    }

    // ==================== 1. getDeptTree 获取部门树 ====================

    @Nested
    @DisplayName("getDeptTree 获取部门树")
    class GetDeptTreeTest {

        @Test
        @DisplayName("空列表：返回空列表")
        void emptyList_shouldReturnEmpty() {
            when(adminDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<AdminDeptVO> result = adminDeptService.getDeptTree();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("有数据：正确构建三级树形结构（总公司→技术部→前端组）")
        void withData_shouldBuildTree() {
            // 构造三级部门：总公司→技术部→前端组
            AdminDept root = buildDept(1L, 0L, "总公司");
            AdminDept child = buildDept(2L, 1L, "技术部");
            AdminDept grandchild = buildDept(3L, 2L, "前端组");
            when(adminDeptMapper.selectList(any()))
                .thenReturn(Arrays.asList(root, child, grandchild));

            List<AdminDeptVO> result = adminDeptService.getDeptTree();

            // 验证：返回1个根节点
            assertThat(result).hasSize(1);
            AdminDeptVO rootVO = result.get(0);
            assertThat(rootVO.getId()).isEqualTo(1L);
            assertThat(rootVO.getName()).isEqualTo("总公司");

            // 验证：根节点有1个子节点
            assertThat(rootVO.getChildren()).hasSize(1);
            AdminDeptVO childVO = rootVO.getChildren().get(0);
            assertThat(childVO.getId()).isEqualTo(2L);
            assertThat(childVO.getName()).isEqualTo("技术部");

            // 验证：子节点有1个孙节点
            assertThat(childVO.getChildren()).hasSize(1);
            AdminDeptVO grandchildVO = childVO.getChildren().get(0);
            assertThat(grandchildVO.getId()).isEqualTo(3L);
            assertThat(grandchildVO.getName()).isEqualTo("前端组");
        }
    }

    // ==================== 2. createDept 新增部门 ====================

    @Nested
    @DisplayName("createDept 新增部门")
    class CreateDeptTest {

        @Test
        @DisplayName("同父部门下名称重复：抛ADMIN_DEPT_NAME_EXISTS异常，不执行insert")
        void nameExists_shouldThrowException() {
            AdminDeptCreateDTO dto = buildCreateDTO("技术部", 0L);
            // 模拟同名部门已存在
            when(adminDeptMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminDeptService.createDept(dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DEPT_NAME_EXISTS.getCode());

            verify(adminDeptMapper, never()).insert(any(AdminDept.class));
        }

        @Test
        @DisplayName("新增成功：parentId和sort为null时默认为0，状态默认为1")
        void success_shouldSetDefaults() {
            AdminDeptCreateDTO dto = new AdminDeptCreateDTO();
            dto.setParentId(null); // 不传父级，应该是顶级
            dto.setSort(null);     // 不传排序号，默认0
            dto.setName("技术部");
            dto.setLeader("张三");
            // 模拟名称不重复
            when(adminDeptMapper.selectCount(any())).thenReturn(0L);
            when(adminDeptMapper.insert(any(AdminDept.class))).thenReturn(1);

            adminDeptService.createDept(dto);

            // 验证：捕获保存的部门对象，检查默认值
            ArgumentCaptor<AdminDept> deptCaptor = ArgumentCaptor.forClass(AdminDept.class);
            verify(adminDeptMapper).insert(deptCaptor.capture());
            AdminDept saved = deptCaptor.getValue();
            // parentId为null时默认为0
            assertThat(saved.getParentId()).isEqualTo(0L);
            // sort为null时默认为0
            assertThat(saved.getSort()).isEqualTo(0);
            // 新增部门默认状态为正常(1)
            assertThat(saved.getStatus()).isEqualTo(1);
        }
    }

    // ==================== 3. updateDept 修改部门 ====================

    @Nested
    @DisplayName("updateDept 修改部门信息")
    class UpdateDeptTest {

        @Test
        @DisplayName("部门不存在：抛ADMIN_DEPT_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            AdminDeptUpdateDTO dto = new AdminDeptUpdateDTO();
            dto.setName("新名字");
            when(adminDeptMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDeptService.updateDept(999L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DEPT_NOT_FOUND.getCode());

            verify(adminDeptMapper, never()).updateById(any(AdminDept.class));
        }

        @Test
        @DisplayName("改名时同父部门下名称重复：抛ADMIN_DEPT_NAME_EXISTS异常")
        void nameExists_shouldThrowException() {
            AdminDept existingDept = buildDept(2L, 1L, "技术部");
            when(adminDeptMapper.selectById(2L)).thenReturn(existingDept);
            // 模拟同父部门下已有同名部门（排除自身后还有1个）
            when(adminDeptMapper.selectCount(any())).thenReturn(1L);

            AdminDeptUpdateDTO dto = new AdminDeptUpdateDTO();
            dto.setName("运营部"); // 改名为已存在的名称

            assertThatThrownBy(() -> adminDeptService.updateDept(2L, dto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DEPT_NAME_EXISTS.getCode());

            verify(adminDeptMapper, never()).updateById(any(AdminDept.class));
        }

        @Test
        @DisplayName("改名时名称与自身相同：不查重，直接更新")
        void sameNameAsSelf_shouldNotCheckDuplicate() {
            AdminDept existingDept = buildDept(2L, 1L, "技术部");
            when(adminDeptMapper.selectById(2L)).thenReturn(existingDept);

            AdminDeptUpdateDTO dto = new AdminDeptUpdateDTO();
            dto.setName("技术部"); // 名称和原来一样，不需要查重

            adminDeptService.updateDept(2L, dto);

            // 验证：没有调用selectCount查重（因为名称没变）
            verify(adminDeptMapper, never()).selectCount(any());
            // 验证：直接调用了updateById
            verify(adminDeptMapper).updateById(any(AdminDept.class));
        }
    }

    // ==================== 4. deleteDept 删除部门 ====================

    @Nested
    @DisplayName("deleteDept 删除部门")
    class DeleteDeptTest {

        @Test
        @DisplayName("有子部门：抛ADMIN_DEPT_HAS_CHILDREN异常，不执行删除")
        void hasChildren_shouldThrowException() {
            AdminDept dept = buildDept(1L, 0L, "技术部");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);
            // 模拟有1个子部门
            when(adminDeptMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminDeptService.deleteDept(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DEPT_HAS_CHILDREN.getCode());

            verify(adminDeptMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("有管理员属于该部门：抛ADMIN_DEPT_HAS_USERS异常")
        void hasUsers_shouldThrowException() {
            AdminDept dept = buildDept(1L, 0L, "技术部");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);
            // 模拟没有子部门
            when(adminDeptMapper.selectCount(any())).thenReturn(0L);
            // 模拟有1个管理员属于该部门
            when(adminUserMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> adminDeptService.deleteDept(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DEPT_HAS_USERS.getCode());

            verify(adminDeptMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除成功：调用deleteById执行逻辑删除")
        void success_shouldCallDeleteById() {
            AdminDept dept = buildDept(1L, 0L, "技术部");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);
            // 模拟没有子部门
            when(adminDeptMapper.selectCount(any())).thenReturn(0L);
            // 模拟没有管理员
            when(adminUserMapper.selectCount(any())).thenReturn(0L);

            adminDeptService.deleteDept(1L);

            // 验证：调用了deleteById（逻辑删除）
            verify(adminDeptMapper).deleteById(1L);
        }
    }

    // ==================== 5. getDeptById 查询部门详情 ====================

    @Nested
    @DisplayName("getDeptById 查询部门详情")
    class GetDeptByIdTest {

        @Test
        @DisplayName("部门不存在：抛ADMIN_DEPT_NOT_FOUND异常")
        void notFound_shouldThrowException() {
            when(adminDeptMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> adminDeptService.getDeptById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ADMIN_DEPT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("查询成功：返回VO")
        void found_shouldReturnVO() {
            AdminDept dept = buildDept(1L, 0L, "技术部");
            when(adminDeptMapper.selectById(1L)).thenReturn(dept);

            AdminDeptVO vo = adminDeptService.getDeptById(1L);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getName()).isEqualTo("技术部");
            assertThat(vo.getParentId()).isEqualTo(0L);
        }
    }
}
