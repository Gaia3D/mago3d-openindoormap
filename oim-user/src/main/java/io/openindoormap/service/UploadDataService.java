package io.openindoormap.service;

import java.util.List;

import io.openindoormap.domain.UploadData;
import io.openindoormap.domain.UploadDataFile;

public interface UploadDataService {

	/**
	 * 업로딩 총 건수
	 * @param uploadData
	 * @return
	 */
	Long getUploadDataTotalCount(UploadData uploadData);
	
	/**
	 * 업로딩 목록
	 * @param uploadData
	 * @return
	 */
	List<UploadData> getListUploadData(UploadData uploadData);
	
	/**
	 * 업로딩 정보
	 * @param uploadData
	 * @return
	 */
	UploadData getUploadData(UploadData uploadData);
	
	/**
	 * 업로딩 파일 정보 목록
	 * @param uploadData
	 * @return
	 */
	List<UploadDataFile> getListUploadDataFile(UploadData uploadData);
	
	/**
	 * 사용자 3차원 파일 업로딩
	 * @param uploadData
	 * @param uploadDataFileList
	 * @return
	 */
	int insertUploadData(UploadData uploadData, List<UploadDataFile> uploadDataFileList);
	
	/**
	 * 업로딩 데이터 삭제
	 * @param userId
	 * @param checkIds
	 * @return
	 */
	int deleteUploadDatas(String userId, String checkIds);
}
