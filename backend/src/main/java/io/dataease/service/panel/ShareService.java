package io.dataease.service.panel;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import io.dataease.auth.api.dto.CurrentRoleDto;
import io.dataease.auth.api.dto.CurrentUserDto;
import io.dataease.auth.service.ExtAuthService;
import io.dataease.commons.constants.SysLogConstants;
import io.dataease.commons.constants.SystemConstants;
import io.dataease.commons.model.AuthURD;
import io.dataease.commons.utils.AuthUtils;
import io.dataease.commons.utils.BeanUtils;
import io.dataease.commons.utils.CommonBeanFactory;
import io.dataease.commons.utils.DeLogUtils;
import io.dataease.controller.request.panel.PanelShareFineDto;
import io.dataease.controller.request.panel.PanelShareRemoveRequest;
import io.dataease.controller.request.panel.PanelShareRequest;
import io.dataease.controller.request.panel.PanelShareSearchRequest;
import io.dataease.controller.sys.base.BaseGridRequest;
import io.dataease.dto.dataset.DatasetShareFineDto;
import io.dataease.dto.panel.PanelShareDto;
import io.dataease.dto.panel.PanelShareOutDTO;
import io.dataease.dto.panel.PanelSharePo;
import io.dataease.ext.ExtAuthMapper;
import io.dataease.ext.ExtPanelShareMapper;
import io.dataease.plugins.common.base.domain.*;
import io.dataease.plugins.common.base.mapper.PanelGroupMapper;
import io.dataease.plugins.common.base.mapper.PanelShareMapper;
import io.dataease.plugins.common.base.mapper.SysAuthDetailMapper;
import io.dataease.plugins.common.base.mapper.SysAuthMapper;
import io.dataease.service.message.DeMsgutil;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static io.dataease.commons.constants.PrivilegeExtendExtend.EXTEND_USE;
import static io.dataease.commons.constants.ResourceAuthLevel.DATASET_LEVEL_USE;
import static io.dataease.commons.constants.ResourceAuthLevel.PANEL_LEVEL_VIEW;
import static io.dataease.commons.constants.SysAuthConstants.*;

@Service
public class ShareService {

    @Autowired(required = false)
    private PanelShareMapper mapper;

    @Resource
    private PanelGroupMapper panelGroupMapper;

    @Resource
    private ExtPanelShareMapper extPanelShareMapper;

    @Resource
    private SysAuthMapper sysAuthMapper;

    @Resource
    private SysAuthDetailMapper sysAuthDetailMapper;

    @Resource
    private ExtAuthMapper extAuthMapper;

    @Autowired
    private ExtAuthService extAuthService;

    /**
     * 1.查询当前节点已经分享给了哪些目标
     * 2.过滤出新增的目标
     * 3.过滤出减少的目标
     * 4.批量删除
     * 5.批量新增
     * 6.发送取消分享消息
     * 7.发送新增分享消息
     *
     * @param panelShareFineDto
     */
    @Transactional
    public void fineSave(PanelShareFineDto panelShareFineDto) {

        List<PanelShare> addShares = new ArrayList<>();// 新增的分享
        List<Long> redShareIdLists = new ArrayList<>();// 取消的分享

        String panelGroupId = panelShareFineDto.getResourceId();
        AuthURD authURD = panelShareFineDto.getAuthURD();
        AuthURD sharedAuthURD = new AuthURD();
        AuthURD addAuthURD = new AuthURD();

        Map<Integer, List<Long>> authURDMap = new HashMap<>();
        authURDMap.put(0, authURD.getUserIds());
        authURDMap.put(1, authURD.getRoleIds());
        authURDMap.put(2, authURD.getDeptIds());
        PanelShareSearchRequest request = new PanelShareSearchRequest();
        request.setCurrentUserName(AuthUtils.getUser().getUsername());
        request.setResourceId(panelGroupId);
        // 当前用户已经分享出去的
        List<PanelShare> panelShares = extPanelShareMapper.queryWithResource(request);
        Map<Integer, List<TempShareNode>> typeSharedMap = panelShares.stream().map(this::convertNode).collect(Collectors.groupingBy(TempShareNode::getType));

        for (Map.Entry<Integer, List<Long>> entry : authURDMap.entrySet()) {
            Integer key = entry.getKey();
            List<TempShareNode> shareNodes;
            if (null == typeSharedMap || null == typeSharedMap.get(key)) {
                shareNodes = new ArrayList<>();
            } else {
                shareNodes = typeSharedMap.get(key);
            }
            List<Long> value = entry.getValue();
            if (null != value) {
                Map<String, Object> dataMap = filterData(value, shareNodes);
                List<Long> newIds = (List<Long>) dataMap.get("add");
                for (int i = 0; i < newIds.size(); i++) {
                    Long id = newIds.get(i);
                    PanelShare share = new PanelShare();
                    share.setCreateTime(System.currentTimeMillis());
                    share.setPanelGroupId(panelGroupId);
                    share.setTargetId(id);
                    share.setType(key);
                    addShares.add(share);
                }
                List<TempShareNode> redNodes = (List<TempShareNode>) dataMap.get("red");
                List<Long> redIds = redNodes.stream().map(TempShareNode::getShareId).distinct().collect(Collectors.toList());
                List<Long> redTargetIds = redNodes.stream().map(TempShareNode::getTargetId).distinct().collect(Collectors.toList());
                redShareIdLists.addAll(redIds);
                buildRedAuthURD(key, redTargetIds, sharedAuthURD);
                buildRedAuthURD(key, newIds, addAuthURD);
            }

        }

        if (CollectionUtils.isNotEmpty(redShareIdLists)) {
            extPanelShareMapper.batchDelete(redShareIdLists);
        }

        if (CollectionUtils.isNotEmpty(addShares)) {
            extPanelShareMapper.batchInsert(addShares, AuthUtils.getUser().getUsername());
        }

        // 取消权限 操作sys_auth表
        if (CollectionUtils.isNotEmpty(redShareIdLists)) {
            redShareSysAuths(panelGroupId, sharedAuthURD);
        }
        // 新增权限 sys_auth表
        if (CollectionUtils.isNotEmpty(addShares)) {
            addShareSysAuths(panelGroupId, addAuthURD);
        }

        PanelGroup panelGroup = panelGroupMapper.selectByPrimaryKey(panelGroupId);

        if (CollectionUtils.isNotEmpty(addAuthURD.getUserIds())) {
            addAuthURD.getUserIds().forEach(id -> {
                if (CollectionUtils.isEmpty(sharedAuthURD.getUserIds()) || !sharedAuthURD.getUserIds().contains(id)) {
                    DeLogUtils.save(SysLogConstants.OPERATE_TYPE.SHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelGroupId, panelGroup.getPid(), id, SysLogConstants.SOURCE_TYPE.USER);
                }
            });
        }
        if (CollectionUtils.isNotEmpty(addAuthURD.getRoleIds())) {
            addAuthURD.getRoleIds().forEach(id -> {
                if (CollectionUtils.isEmpty(sharedAuthURD.getRoleIds()) || !sharedAuthURD.getRoleIds().contains(id)) {
                    DeLogUtils.save(SysLogConstants.OPERATE_TYPE.SHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelGroupId, panelGroup.getPid(), id, SysLogConstants.SOURCE_TYPE.ROLE);
                }
            });
        }
        if (CollectionUtils.isNotEmpty(addAuthURD.getDeptIds())) {
            addAuthURD.getDeptIds().forEach(id -> {
                if (CollectionUtils.isEmpty(sharedAuthURD.getDeptIds()) || !sharedAuthURD.getDeptIds().contains(id)) {
                    DeLogUtils.save(SysLogConstants.OPERATE_TYPE.SHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelGroupId, panelGroup.getPid(), id, SysLogConstants.SOURCE_TYPE.DEPT);
                }
            });
        }

        if (CollectionUtils.isNotEmpty(sharedAuthURD.getUserIds())) {
            sharedAuthURD.getUserIds().forEach(id -> {
                if (CollectionUtils.isEmpty(addAuthURD.getUserIds()) || !addAuthURD.getUserIds().contains(id)) {
                    DeLogUtils.save(SysLogConstants.OPERATE_TYPE.UNSHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelGroupId, panelGroup.getPid(), id, SysLogConstants.SOURCE_TYPE.USER);
                }
            });
        }

        if (CollectionUtils.isNotEmpty(sharedAuthURD.getRoleIds())) {
            sharedAuthURD.getRoleIds().forEach(id -> {
                if (CollectionUtils.isEmpty(addAuthURD.getRoleIds()) || !addAuthURD.getRoleIds().contains(id)) {
                    DeLogUtils.save(SysLogConstants.OPERATE_TYPE.UNSHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelGroupId, panelGroup.getPid(), id, SysLogConstants.SOURCE_TYPE.ROLE);
                }
            });
        }
        if (CollectionUtils.isNotEmpty(sharedAuthURD.getDeptIds())) {
            sharedAuthURD.getDeptIds().forEach(id -> {
                if (CollectionUtils.isEmpty(addAuthURD.getDeptIds()) || !addAuthURD.getDeptIds().contains(id)) {
                    DeLogUtils.save(SysLogConstants.OPERATE_TYPE.UNSHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelGroupId, panelGroup.getPid(), id, SysLogConstants.SOURCE_TYPE.DEPT);
                }
            });
        }


        // 以上是业务代码
        // 下面是消息发送
        Set<Long> addUserIdSet = AuthUtils.userIdsByURD(addAuthURD);
        Set<Long> redUserIdSet = AuthUtils.userIdsByURD(sharedAuthURD);

        CurrentUserDto user = AuthUtils.getUser();
        Gson gson = new Gson();
        String msg = panelGroup.getName();

        List<String> msgParam = new ArrayList<>();
        msgParam.add(panelGroupId);
        addUserIdSet.forEach(userId -> {
            if (!redUserIdSet.contains(userId) && !user.getUserId().equals(userId)) {
                DeMsgutil.sendMsg(userId, 2L, user.getNickName() + " 分享了仪表板【" + msg + "】，请查收!", gson.toJson(msgParam));
            }
        });

        redUserIdSet.forEach(userId -> {
            if (!addUserIdSet.contains(userId) && !user.getUserId().equals(userId)) {
                DeMsgutil.sendMsg(userId, 3L, user.getNickName() + " 取消分享了仪表板【" + msg + "】，请查收!",
                                  gson.toJson(msgParam));
            }
        });

    }

    private void redShareSysAuths(String authSource, AuthURD sharedAuthURD) {
        if (CollectionUtils.isNotEmpty(sharedAuthURD.getUserIds())) {
            for (Long userId : sharedAuthURD.getUserIds()) {
                redSysAuth(authSource, Long.toString(userId), AUTH_TARGET_USER);
                extAuthService.clearUserResource(userId);
            }
        }
        if (CollectionUtils.isNotEmpty(sharedAuthURD.getDeptIds())) {
            for (Long deptId : sharedAuthURD.getDeptIds()) {
                redSysAuth(authSource, Long.toString(deptId), AUTH_TARGET_DEPT);
                extAuthService.clearDeptResource(deptId);
            }
        }
        if (CollectionUtils.isNotEmpty(sharedAuthURD.getRoleIds())) {
            for (Long roleId : sharedAuthURD.getRoleIds()) {
                redSysAuth(authSource, Long.toString(roleId), AUTH_TARGET_ROLE);
                extAuthService.clearRoleResource(roleId);
            }
        }
    }

    private void redSysAuth(String authSource, String authTarget, String authTargetType) {
        SysAuthExample sysAuthExample = new SysAuthExample();
        sysAuthExample.createCriteria()
                      .andAuthSourceEqualTo(authSource)
                      .andAuthTargetEqualTo(authTarget)
                      .andAuthTargetTypeEqualTo(authTargetType);
        List<SysAuth> sysAuths = sysAuthMapper.selectByExample(sysAuthExample);
        List<String> authIds = sysAuths.stream().map(a -> a.getId()).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(authIds)) {
            return;
        }
        SysAuthDetailExample sysAuthDetailExample = new SysAuthDetailExample();
        sysAuthDetailExample.createCriteria().andAuthIdIn(authIds);
        sysAuthDetailMapper.deleteByExample(sysAuthDetailExample);
        sysAuthMapper.deleteByExample(sysAuthExample);
    }

    private void addShareSysAuths(String authSource, AuthURD addAuthURD) {
        if (CollectionUtils.isNotEmpty(addAuthURD.getUserIds())) {
            for (Long userId : addAuthURD.getUserIds()) {
                addSysAuthPanel(authSource, Long.toString(userId), AUTH_TARGET_USER);
                extAuthService.clearUserResource(userId);
            }
        }
        if (CollectionUtils.isNotEmpty(addAuthURD.getDeptIds())) {
            for (Long deptId : addAuthURD.getDeptIds()) {
                addSysAuthPanel(authSource, Long.toString(deptId), AUTH_TARGET_DEPT);
                extAuthService.clearDeptResource(deptId);
            }
        }
        if (CollectionUtils.isNotEmpty(addAuthURD.getRoleIds())) {
            for (Long roleId : addAuthURD.getRoleIds()) {
                addSysAuthPanel(authSource, Long.toString(roleId), AUTH_TARGET_ROLE);
                extAuthService.clearRoleResource(roleId);
            }
        }
    }

    private void addShareDatasetSysAuths(String authSource, AuthURD addAuthURD) {
        if (CollectionUtils.isNotEmpty(addAuthURD.getUserIds())) {
            for (Long userId : addAuthURD.getUserIds()) {
                addSysAuthDataset(authSource, Long.toString(userId), AUTH_TARGET_USER);
                extAuthService.clearUserResource(userId);
            }
        }
        if (CollectionUtils.isNotEmpty(addAuthURD.getDeptIds())) {
            for (Long deptId : addAuthURD.getDeptIds()) {
                addSysAuthDataset(authSource, Long.toString(deptId), AUTH_TARGET_DEPT);
                extAuthService.clearDeptResource(deptId);
            }
        }
        if (CollectionUtils.isNotEmpty(addAuthURD.getRoleIds())) {
            for (Long roleId : addAuthURD.getRoleIds()) {
                addSysAuthDataset(authSource, Long.toString(roleId), AUTH_TARGET_ROLE);
                extAuthService.clearRoleResource(roleId);
            }
        }
    }

    private void addSysAuthDataset(String authSource, String authTarget, String authTargetType) {
        addSysAuth(authSource,
                   authTarget,
                   AUTH_SOURCE_TYPE_DATASET,
                   authTargetType,
                   EXTEND_USE.getExtend(),
                   ListUtil.of(DATASET_LEVEL_USE.getLevel()));
    }

    private void addSysAuthPanel(String authSource, String authTarget, String authTargetType) {
        addSysAuth(authSource,
                   authTarget,
                   AUTH_SOURCE_TYPE_PANEL,
                   authTargetType,
                   null,
                   ListUtil.of(PANEL_LEVEL_VIEW.getLevel()));
    }

    private void addSysAuth(String authSource,
                            String authTarget,
                            String authSourceType,
                            String authTargetType,
                            String privilegeExtend,
                            List<Integer> privileges) {
        SysAuth sysAuth = new SysAuth();
        sysAuth.setId(UUID.randomUUID().toString());
        sysAuth.setAuthSource(authSource);
        sysAuth.setAuthSourceType(authSourceType);
        sysAuth.setAuthTarget(authTarget);
        sysAuth.setAuthTargetType(authTargetType);
        sysAuth.setAuthTime(System.currentTimeMillis());
        sysAuth.setUpdateTime(new Date());
        sysAuth.setAuthUser(AuthUtils.getUser().getUsername());
        sysAuthMapper.insert(sysAuth);
        for (Integer privilege : privileges) {
            SysAuthDetail sysAuthDetail = new SysAuthDetail();
            sysAuthDetail.setId(UUID.randomUUID().toString());
            sysAuthDetail.setAuthId(sysAuth.getId());
            sysAuthDetail.setCreateUser(sysAuth.getAuthUser());
            sysAuthDetail.setPrivilegeType(privilege);
            sysAuthDetail.setPrivilegeExtend(privilegeExtend);
            sysAuthDetail.setPrivilegeValue(SystemConstants.PRIVILEGE_VALUE.ON);
            sysAuthDetail.setCreateTime(System.currentTimeMillis());
            sysAuthDetailMapper.insert(sysAuthDetail);
        }
    }

    private void buildRedAuthURD(Integer type, List<Long> redIds, AuthURD authURD) {
        if (type == 0) {
            authURD.setUserIds(redIds);
        }
        if (type == 1) {
            authURD.setRoleIds(redIds);
        }
        if (type == 2) {
            authURD.setDeptIds(redIds);
        }
    }

    /**
     * @param newTargets 新的分享目标
     * @param shareNodes 已景分享目标
     * @return
     */
    private Map<String, Object> filterData(List<Long> newTargets, List<TempShareNode> shareNodes) {
        Map<String, Object> result = new HashMap<>();
        List<Long> newUserIds = new ArrayList<>();
        for (int i = 0; i < newTargets.size(); i++) {
            Long newTargetId = newTargets.get(i);
            Boolean isNew = true;
            for (int j = 0; j < shareNodes.size(); j++) {
                TempShareNode shareNode = shareNodes.get(j);
                Long sharedId = shareNode.getTargetId();
                if (newTargetId.equals(sharedId)) {
                    shareNode.setMatched(true); // 已分享 重新命中
                    isNew = false;
                }
            }
            if (isNew) {
                // 获取新增的
                newUserIds.add(newTargetId);
            }
        }
        // 获取需要取消分享的
        List<TempShareNode> missNodes = shareNodes.stream().filter(item -> !item.getMatched())
                                                  .collect(Collectors.toList());
        result.put("add", newUserIds);
        result.put("red", missNodes);
        return result;
    }

    /**
     * 分享数据源
     *
     * @param datasetShareFineDto
     */
    @Transactional
    public void fineSave(DatasetShareFineDto datasetShareFineDto) {
        if (StringUtils.equals("spine", datasetShareFineDto.getSharedNodeType())) {
        }
        for (String resourceId : datasetShareFineDto.getResourceId()) {
            fineSaveByResourceId(resourceId, datasetShareFineDto.getAuthURD());
        }
    }

    /**
     * 处理单个资源的分享操作
     *
     * @param datasetResId
     * @param authURD
     */
    private void fineSaveByResourceId(String datasetResId, AuthURD authURD) {
        AuthURD redAuthURD = new AuthURD();
        redAuthURD.setUserIds(Lists.newArrayList());
        redAuthURD.setRoleIds(Lists.newArrayList());
        redAuthURD.setDeptIds(Lists.newArrayList());
        AuthURD addAuthURD = new AuthURD();
        addAuthURD.setUserIds(Lists.newArrayList());
        addAuthURD.setRoleIds(Lists.newArrayList());
        addAuthURD.setDeptIds(Lists.newArrayList());
        // 查询 datasetResId 分享的目标列表 该资源授权情况
        List<SysAuth> sysAuthList = extAuthMapper.queryByResource(datasetResId);
        Set<Long> userIds = CollectionUtils.isEmpty(authURD.getUserIds())
                            ? Sets.newHashSet()
                            : authURD.getUserIds().stream().collect(Collectors.toSet());
        Set<Long> roleIds = CollectionUtils.isEmpty(authURD.getRoleIds())
                            ? Sets.newHashSet()
                            : authURD.getRoleIds().stream().collect(Collectors.toSet());
        Set<Long> deptIds = CollectionUtils.isEmpty(authURD.getDeptIds())
                            ? Sets.newHashSet()
                            : authURD.getDeptIds().stream().collect(Collectors.toSet());
        // 根据 authURD 判断，哪些被移除（取消分享），哪些新增（新增分享），去掉重复的（已存在的不需要操作）
        buildRedSysAuths(redAuthURD, sysAuthList, userIds, roleIds, deptIds);
        buildAddSysAuths(addAuthURD, sysAuthList, userIds, roleIds, deptIds);
        // 取消权限 操作sys_auth表
        if (hasAuthURD(redAuthURD)) {
            redShareSysAuths(datasetResId, redAuthURD);
        }
        // 新增权限 sys_auth表
        if (hasAuthURD(addAuthURD)) {
            addShareDatasetSysAuths(datasetResId, addAuthURD);
        }
    }

    private void buildRedSysAuths(AuthURD redAuthURD, List<SysAuth> sysAuthList, Set<Long> userIds, Set<Long> roleIds, Set<Long> deptIds) {
        for (SysAuth sysAuth : sysAuthList) {
            Long sysAuthTarget = Long.valueOf(sysAuth.getAuthTarget());
            if (StrUtil.equals(sysAuth.getAuthTargetType(), AUTH_TARGET_USER)) {
                if (!StrUtil.equals(sysAuth.getAuthUser(), "auto") &&
                    !userIds.contains(sysAuthTarget)) {
                    redAuthURD.getUserIds().add(sysAuthTarget);
                }
            } else if (!StrUtil.equals(sysAuth.getAuthUser(), "auto") &&
                       StrUtil.equals(sysAuth.getAuthTargetType(), AUTH_TARGET_ROLE)) {
                if (!roleIds.contains(sysAuthTarget)) {
                    redAuthURD.getRoleIds().add(sysAuthTarget);
                }
            } else if (!StrUtil.equals(sysAuth.getAuthUser(), "auto") &&
                       StrUtil.equals(sysAuth.getAuthTargetType(), AUTH_TARGET_DEPT)) {
                if (!deptIds.contains(sysAuthTarget)) {
                    redAuthURD.getDeptIds().add(sysAuthTarget);
                }
            }
        }
    }

    private void buildAddSysAuths(AuthURD addAuthURD, List<SysAuth> sysAuthList, Set<Long> userIds, Set<Long> roleIds, Set<Long> deptIds) {
        Set<String> sysUserIds = sysAuthList.stream()
                                            .filter(v -> StrUtil.equals(v.getAuthTargetType(), AUTH_TARGET_USER))
                                            .map(v -> v.getAuthTarget()).collect(Collectors.toSet());
        for (Long userId : userIds) {
            if (!sysUserIds.contains(Long.toString(userId))) {
                addAuthURD.getUserIds().add(userId);
            }
        }
        Set<String> sysRoleIds = sysAuthList.stream()
                                            .filter(v -> StrUtil.equals(v.getAuthTargetType(), AUTH_TARGET_ROLE))
                                            .map(v -> v.getAuthTarget()).collect(Collectors.toSet());
        for (Long roleId : roleIds) {
            if (!sysRoleIds.contains(Long.toString(roleId))) {
                addAuthURD.getRoleIds().add(roleId);
            }
        }
        Set<String> sysDeptIds = sysAuthList.stream()
                                            .filter(v -> StrUtil.equals(v.getAuthTargetType(), AUTH_TARGET_DEPT))
                                            .map(v -> v.getAuthTarget()).collect(Collectors.toSet());
        for (Long deptId : deptIds) {
            if (!sysDeptIds.contains(Long.toString(deptId))) {
                addAuthURD.getDeptIds().add(deptId);
            }
        }
    }

    private boolean hasAuthURD(AuthURD authURD) {
        return CollectionUtils.isNotEmpty(authURD.getDeptIds()) ||
               CollectionUtils.isNotEmpty(authURD.getUserIds()) ||
               CollectionUtils.isNotEmpty(authURD.getRoleIds());
    }

    @Data
    private class TempShareNode {
        private Long shareId;
        private Integer type;
        private Long targetId;
        private Boolean matched = false;

        public boolean targetMatch(Long tid) {
            return targetId.equals(tid);
        }
    }

    private TempShareNode convertNode(PanelShare panelShare) {
        return BeanUtils.copyBean(new TempShareNode(), panelShare);
    }

    @Transactional
    public void save(PanelShareRequest request) {
        List<PanelGroup> panelGroups = queryGroup(request.getPanelIds());
        // 1.先根据仪表板删除所有已经分享的
        Integer type = request.getType();
        List<String> panelIds = request.getPanelIds();
        List<Long> targetIds = request.getTargetIds();
        // 使用原生对象会导致事物失效 所以这里需要使用spring代理对象
        if (CollectionUtils.isNotEmpty(panelIds)) {
            ShareService proxy = CommonBeanFactory.getBean(ShareService.class);
            panelIds.forEach(panelId -> proxy.delete(panelId, type));
        }
        if (CollectionUtils.isEmpty(targetIds))
            return;

        long now = System.currentTimeMillis();
        List<PanelShare> shares = panelIds.stream().flatMap(panelId -> targetIds.stream().map(targetId -> {
            PanelShare share = new PanelShare();
            share.setCreateTime(now);
            share.setPanelGroupId(panelId);
            share.setTargetId(targetId);
            share.setType(type);
            return share;
        })).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(shares)) {
            extPanelShareMapper.batchInsert(shares, AuthUtils.getUser().getUsername());
        }

        // 下面是发送提醒消息逻辑
        Set<Long> userIdSet;
        AuthURD authURD = new AuthURD();
        if (type == 0) {
            authURD.setUserIds(targetIds);
        }
        if (type == 1) {
            authURD.setRoleIds(targetIds);
        }
        if (type == 2) {
            authURD.setDeptIds(targetIds);
        }
        userIdSet = AuthUtils.userIdsByURD(authURD);

        CurrentUserDto user = AuthUtils.getUser();
        String msg = StringUtils.joinWith("，",
                                          panelGroups.stream().map(PanelGroup::getName).collect(Collectors.toList()));
        Gson gson = new Gson();
        userIdSet.forEach(userId -> DeMsgutil.sendMsg(userId, 2L, user.getNickName() + " 分享了仪表板【" + msg + "】给您，请查收!",
                                                      gson.toJson(panelIds)));

    }

    private List<PanelGroup> queryGroup(List<String> panelIds) {
        return panelIds.stream().map(panelGroupMapper::selectByPrimaryKey).collect(Collectors.toList());
    }

    /**
     * panel_group_id建了索引 效率不会很差
     *
     * @param panel_group_id
     */
    @Transactional
    public void delete(String panel_group_id, Integer type) {
        PanelShareExample example = new PanelShareExample();
        PanelShareExample.Criteria criteria = example.createCriteria();
        criteria.andPanelGroupIdEqualTo(panel_group_id);
        if (type != null) {
            criteria.andTypeEqualTo(type);
        }
        mapper.deleteByExample(example);
    }

    public List<PanelSharePo> shareOut() {
        return null;
    }

    public List<PanelSharePo> queryShareOut() {
        String username = AuthUtils.getUser().getUsername();
        return extPanelShareMapper.queryOut(username);
    }

    public List<PanelShareDto> queryTree(BaseGridRequest request) {
        CurrentUserDto user = AuthUtils.getUser();
        Long userId = user.getUserId();
        Long deptId = user.getDeptId();
        List<Long> roleIds = user.getRoles().stream().map(CurrentRoleDto::getId).collect(Collectors.toList());

        Map<String, Object> param = new HashMap<>();
        param.put("userId", userId);
        param.put("deptId", deptId);
        param.put("roleIds", CollectionUtils.isNotEmpty(roleIds) ? roleIds : null);

        List<PanelSharePo> data = extPanelShareMapper.query(param);
        List<PanelShareDto> dtoLists = data.stream().map(po -> BeanUtils.copyBean(new PanelShareDto(), po))
                                           .collect(Collectors.toList());
        return convertTree(dtoLists);
    }

    // List构建Tree
    private List<PanelShareDto> convertTree(List<PanelShareDto> data) {
        String username = AuthUtils.getUser().getUsername();
        Map<String, List<PanelShareDto>> map = data.stream()
                                                   .filter(panelShareDto -> StringUtils.isNotEmpty(panelShareDto.getCreator())
                                                                            && !StringUtils.equals(username, panelShareDto.getCreator()))
                                                   .collect(Collectors.groupingBy(PanelShareDto::getCreator));
        return map.entrySet().stream().map(entry -> {
            PanelShareDto panelShareDto = new PanelShareDto();
            panelShareDto.setName(entry.getKey());
            panelShareDto.setChildren(entry.getValue());
            return panelShareDto;
        }).collect(Collectors.toList());
    }

    public List<PanelShare> queryWithResource(PanelShareSearchRequest request) {
        if (StrUtil.equals(request.getResourceType(), AUTH_SOURCE_TYPE_DATASET)) {
            List<String> resourceIds = request.getResourceIds();
            Set<Long> resourceIdSet = Sets.newHashSet();
            for (String resourceId : resourceIds) {
                List<SysAuth> sysAuthList = extAuthMapper.queryByResource(resourceId);
                sysAuthList.forEach(s -> resourceIdSet.add(Long.valueOf(s.getAuthTarget())));
            }
            return resourceIdSet.stream().map(s -> {
                PanelShare share = new PanelShare();
                share.setTargetId(s);
                return share;
            }).collect(Collectors.toList());
        }
        String username = AuthUtils.getUser().getUsername();
        request.setCurrentUserName(username);
        return extPanelShareMapper.queryWithResource(request);
    }

    public List<PanelShareOutDTO> queryTargets(String panelId) {
        String username = AuthUtils.getUser().getUsername();
        List<PanelShareOutDTO> targets = extPanelShareMapper.queryTargets(panelId, username);
        if (CollectionUtils.isEmpty(targets))
            return new ArrayList<>();
        return targets.stream().filter(item -> StringUtils.isNotEmpty(item.getTargetName()))
                      .collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(item -> item.getPanelId() + item.getType() + item.getTargetId()))),
                                                            ArrayList::new));
    }

    public void removeSharesyPanel(String panelId) {
        PanelGroup panelGroup = panelGroupMapper.selectByPrimaryKey(panelId);
        PanelShareRemoveRequest request = new PanelShareRemoveRequest();
        request.setPanelId(panelId);
        List<PanelShareOutDTO> panelShareOutDTOS = queryTargets(panelId);
        if (CollectionUtils.isEmpty(panelShareOutDTOS) || ObjectUtils.isEmpty(panelGroup)) {
            return;
        }
        extPanelShareMapper.removeShares(request);
        if (StringUtils.isBlank(panelGroup.getName())) {
            return;
        }
        panelShareOutDTOS.forEach(shareOut -> {
            SysLogConstants.SOURCE_TYPE buiType = buiType(shareOut.getType());
            DeLogUtils.save(SysLogConstants.OPERATE_TYPE.UNSHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelId, panelGroup.getPid(), shareOut.getTargetId(), buiType);
        });

        Map<Integer, List<PanelShareOutDTO>> listMap = panelShareOutDTOS.stream().collect(Collectors.groupingBy(dto -> dto.getType()));
        AuthURD urd = new AuthURD();
        for (Map.Entry<Integer, List<PanelShareOutDTO>> entry : listMap.entrySet()) {
            List<PanelShareOutDTO> dtoList = entry.getValue();
            if (CollectionUtils.isNotEmpty(dtoList)) {
                List<Long> curTargetIds = dtoList.stream().map(dto -> Long.parseLong(dto.getTargetId())).collect(Collectors.toList());
                buildRedAuthURD(entry.getKey(), curTargetIds, urd);
            }
        }
        Set<Long> userIds = AuthUtils.userIdsByURD(urd);
        if (CollectionUtils.isNotEmpty(userIds)) {
            CurrentUserDto user = AuthUtils.getUser();
            Gson gson = new Gson();
            userIds.forEach(userId -> {
                if (!user.getUserId().equals(userId)) {
                    String msg = panelGroup.getName();
                    List<String> msgParam = new ArrayList<>();
                    msgParam.add(panelId);
                    DeMsgutil.sendMsg(userId, 3L, user.getNickName() + " 取消分享了仪表板【" + msg + "】，请查收!", gson.toJson(msgParam));
                }
            });
        }
    }

    private SysLogConstants.SOURCE_TYPE buiType(Integer type) {
        SysLogConstants.SOURCE_TYPE targetType = SysLogConstants.SOURCE_TYPE.USER;
        if (type == 1) {
            targetType = SysLogConstants.SOURCE_TYPE.ROLE;
        } else if (type == 2) {
            targetType = SysLogConstants.SOURCE_TYPE.DEPT;
        }
        return targetType;
    }

    @Transactional
    public void removeShares(PanelShareRemoveRequest removeRequest) {
        String panelId = removeRequest.getPanelId();
        PanelGroup panelGroup = panelGroupMapper.selectByPrimaryKey(panelId);

        extPanelShareMapper.removeShares(removeRequest);

        SysLogConstants.SOURCE_TYPE targetType = buiType(removeRequest.getType());

        DeLogUtils.save(SysLogConstants.OPERATE_TYPE.UNSHARE, SysLogConstants.SOURCE_TYPE.PANEL, panelId, panelGroup.getPid(), removeRequest.getTargetId(), targetType);

        AuthURD sharedAuthURD = new AuthURD();
        List<Long> removeIds = new ArrayList<Long>() {{
            add(removeRequest.getTargetId());
        }};
        buildRedAuthURD(removeRequest.getType(), removeIds, sharedAuthURD);
        CurrentUserDto user = AuthUtils.getUser();
        Gson gson = new Gson();

        String msg = panelGroup.getName();

        List<String> msgParam = new ArrayList<>();
        msgParam.add(panelId);
        Set<Long> redIds = AuthUtils.userIdsByURD(sharedAuthURD);
        redIds.forEach(userId -> {
            if (!user.getUserId().equals(userId)) {
                DeMsgutil.sendMsg(userId, 3L, user.getNickName() + " 取消分享了仪表板【" + msg + "】，请查收!",
                                  gson.toJson(msgParam));
            }
        });

    }

}
