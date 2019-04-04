package io.openindoormap.service.impl;

import java.io.File;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.openindoormap.domain.DataInfo;
import io.openindoormap.domain.Project;
import io.openindoormap.domain.UserPolicy;
import io.openindoormap.persistence.DataMapper;
import io.openindoormap.persistence.ProjectMapper;
import io.openindoormap.service.ProjectService;
// import io.openindoormap.service.UserPolicyService;

import lombok.extern.slf4j.Slf4j;

/**
 * 프로젝트 관리
 * 
 * @author jeongdae
 *
 */
@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

	// @Autowired
	// private UserPolicyService userPolicyService;

	@Autowired
	private ProjectMapper projectMapper;

	@Autowired
	private DataMapper dataMapper;

	/**
	 * 프로젝트 총건수
	 * @param project
	 * @return
	 */
	@Transactional(readOnly = true)
	public Long getProjectTotalCount(Project project) {
		return projectMapper.getProjectTotalCount(project);
	}
	
	/**
	 * 프로젝트 목록
	 * 
	 * @param project
	 * @return
	 */
	@Transactional(readOnly = true)
	public List<Project> getListProject(Project project) {
		return projectMapper.getListProject(project);
	}
	
	/**
	 * 기본 프로젝트 목록
	 * @param projectIds
	 * @return
	 */
	public List<Project> getListDefaultProject(String[] projectIds) {
		return projectMapper.getListDefaultProject(projectIds);
	}
	
	/**
	 * geo 정보를 이용해서 가장 가까운 프로젝트 정보를 취득
	 * @param project
	 * @return
	 */
	@Transactional(readOnly = true)
	public Project getProjectByGeo(Project project) {
		return projectMapper.getProjectByGeo(project);
	}
	
	/**
	 * 프로젝트 조회
	 * 
	 * @param project
	 * @return
	 */
	@Transactional(readOnly = true)
	public Project getProject(Project project) {
		return projectMapper.getProject(project);
	}
	
	/**
	 * Project Key 중복 건수
	 * @param project_key
	 * @return
	 */
	@Transactional(readOnly=true)
	public Integer getDuplicationKeyCount(String project_key) {
		return projectMapper.getDuplicationKeyCount(project_key);
	}

	/**
	 * Project 등록
	 * 
	 * @param project
	 * @return
	 */
	@Transactional
	public int insertProject(Project project) {
		
		// Policy policy = CacheManager.getPolicy();
		// TODO 시연 때문에 일단 임시 경로로 함
//		String makedDirectory = FileUtil.makeDirectory(project.getUser_id(), UploadDirectoryType.YEAR_MONTH, policy.getGeo_data_path() + File.separator);
//		FileUtil.makeDirectory(makedDirectory + project.getProject_key());
//		project.setProject_path(makedDirectory + project.getProject_key() + File.separator);
		//FileUtil.makeDirectory(policy.getGeo_data_path() + File.separator + project.getProject_key());
		project.setProject_path(project.getProject_key() + File.separator);
		int result = projectMapper.insertProject(project);
		
		DataInfo dataInfo = new DataInfo();
		dataInfo.setProject_id(project.getProject_id());
		dataInfo.setSharing_type(project.getSharing_type());
		dataInfo.setData_key(project.getProject_key());
		dataInfo.setData_name(project.getProject_name());
		dataInfo.setUser_id(project.getUser_id());
		dataInfo.setParent(0l);
		dataInfo.setDepth(1);
		dataInfo.setView_order(1);
		dataInfo.setAttributes(project.getAttributes());
		dataMapper.insertData(dataInfo);
		
		return result;
	}

	/**
	 * Project 수정
	 * 
	 * @param project
	 * @return
	 */
	@Transactional
	public int updateProject(Project project) {
		return projectMapper.updateProject(project);
	}

	/**
	 * Project 삭제
	 * 
	 * @param project
	 * @return
	 */
	@Transactional
	public int deleteProject(Project project) {
		
		// TODO 시작 프로젝트가 있으면 수정해야 함
		
		// TODO 프로젝트에 속한 데이터들은 삭제해야 하나?
		// project 이름으로 등록된 최상위 data를 삭제
		dataMapper.deleteDataByProjectId(project);
		
		// 프로젝트 디렉토리 삭제
		// UserPolicy userPolicy = userPolicyService.getUserPolicy(project.getUser_id());
		// File projectDirectory = new File(userPolicy.getGeo_data_path() + File.separator + project.getProject_id());
		// if(!projectDirectory.exists()) {
		// 	projectDirectory.delete();
		// }
		
		return projectMapper.deleteProject(project);
	}
}
