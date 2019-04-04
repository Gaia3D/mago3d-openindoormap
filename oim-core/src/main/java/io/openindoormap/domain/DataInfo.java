package io.openindoormap.domain;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Data 정보
 * @author Cheon JeongDae
 *
 */
@Getter
@Setter
@ToString(callSuper=true)
public class DataInfo extends SearchFilter {
	
	// Data 상태가 사용중
	public static final String STATUS_USE = "0";
	// Data 상태가 중지(관리자)
	public static final String STATUS_FORBID = "1";
	// Data 상태가 삭제(화면 비표시)
	public static final String STATUS_ETC = "2";
	
	// data_group 에 등록되지 않은 Data
	private String[] data_all_id;
	// data_group 에 등록된 Data
	private String[] data_select_id;
	
	/******** 화면 오류 표시용 ********/
	private String message_code;
	private String error_code;
	// 아이디 중복 확인 hidden 값
	private String duplication_value;
	// 논리 삭제 
	private String delete_flag;
	
	// 사용자명
	private String user_id;
	private String user_name;
	
	/****** validator ********/
	private String method_mode;

	// 고유번호
	private Long data_id;
	// Data project 고유번호
	private Integer project_id;
	// Data project 이름
	private String project_name;
	// data 고유 식별번호
	private String data_key;
	// data 고유 식별번호
	private String old_data_key;
	// 부모 data 고유 식별번호
	private String parent_data_key;
	// data 이름
	private String data_name;
	// 공유 타입. 0 : common, 1: public, 2 : private, 3 : sharing
	private String sharing_type;
	// 부모 고유번호
	private Long parent;
	// 부모 이름(화면 표시용)
	private String parent_name;
	// 부모 깊이
	private Integer parent_depth;
	// 깊이
	private Integer depth;
	// 나열 순서
	private Integer view_order;
	// 자식 존재 유무, Y : 존재, N : 존재안함(기본)
	private String child_yn;
	// origin : latitude, longitude, height 를 origin에 맟춤. boundingboxcenter : latitude, longitude, height 를 boundingboxcenter에 맟춤.
	private String mapping_type;
	// 위도, 경도 정보 geometry 타입
	private String location;
	// 위도
	private BigDecimal latitude;
	// 경도
	private BigDecimal longitude;
	// 높이
	private BigDecimal height;
	// heading
	private BigDecimal heading;
	// pitch
	private BigDecimal pitch;
	// roll
	private BigDecimal roll;
	// Data Control 속성
	private String attributes;
	// data 상태. 0:사용중, 1:사용중지(관리자), 2:기타
	private String status;
	// 사용유무, Y : 사용, N : 사용안함
	private String use_yn;
	// 공개 유무. 기본값 비공개 N
	private String public_yn;
	// data 등록 방법. 기본 : SELF
	private String data_insert_type;
	// 설명
	private String description;
	// 수정일 
	private String update_date;
	// 등록일
	private String insert_date;
	
	private String search_data_name;
	private String search_except_data_name;
	
	public String getViewDataInsertType() {
		// TODO 이건 뭔가 아닌거 같은데... 어떻게 처리 하지?
		if(this.data_insert_type == null || "".equals(this.data_insert_type)) {
			return "";
		}
		
		Map<String, Object> commonCodeMap = CacheManager.getCommonCodeMap();
		CommonCode commonCode = (CommonCode)commonCodeMap.get(this.data_insert_type);
		if(commonCode == null) return "";
		else return commonCode.getCode_name();
	}
	
	public String getViewAttributes() {
		if(this.attributes == null || "".equals( attributes) || attributes.length() < 20) {
			return attributes;
		}
		return attributes.substring(0, 20) + "...";
	}
	
	public String getViewInsertDate() {
		if(this.insert_date == null || "".equals( insert_date)) {
			return "";
		}
		return insert_date.substring(0, 19);
	}
}
