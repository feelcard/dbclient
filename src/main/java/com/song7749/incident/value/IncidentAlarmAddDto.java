package com.song7749.incident.value;

import java.util.List;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Length;

import com.song7749.base.AbstractDto;
import com.song7749.base.SendMethod;
import com.song7749.base.YN;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("알람등록")
public class IncidentAlarmAddDto extends AbstractDto {

	private static final long serialVersionUID = 5818973514704170127L;

	@ApiModelProperty(required=true,position=1,value="알람명칭 || 알람의 제목을 작성 120자 이내로 작성")
	@NotBlank
	@Length(max = 120)
	private String subject;

	@ApiModelProperty(required=true,position=2,value="알람 감지 SQL || Y가 리턴되도록 작성, EX) SELECT 'Y' as enable FROM dual")
	@NotBlank
	@Length(max = 8000)
	private String beforeSql;

	@ApiModelProperty(required=true,position=3,value="알람 내역 SQL || 담당자에게 전송할 내용을 생성하는 SQL. 내용이 100byte 넘을 경우 email로 전환")
	@NotBlank
	@Length(max = 8000)
	private String runSql;

	@ApiModelProperty(required=true,position=4,value="알람 방법 || EMAIL or SMS[MMS]")
	@NotNull
	@Enumerated(EnumType.STRING)
	private SendMethod sendMethod;

	@ApiModelProperty(required=true,position=5,value="동작여부 || 현재 실행 중인 경우 실행완료 후 동작 중지")
	@NotNull
	@Enumerated(EnumType.STRING)
	private YN enableYN;

	@ApiModelProperty(required=true,position=6,value="알람 주기 || crontab 양식, * * * * * * (초 분 시 날짜 달 주의날짜) <a href='https://www.freeformatter.com/cron-expression-generator-quartz.html' target='_blank'>cron 확인</a>")
	@NotBlank
	private String schedule;

	@ApiModelProperty(required=true,position=7,value="데이터베이스 || 실행할 데이터베이스를 선택")
	@NotNull
	private Long databaseId;

	@ApiModelProperty(required=true,position=8,value="등록자ID",hidden=true)
	private Long memberId;

	@ApiModelProperty(required=true,position=9,value="전송대상자ID || ID 를 , 형식으로 복수 입력. EX) 1,2,3,4")
	@NotNull
	private List<Long> sendMemberIds;

	public IncidentAlarmAddDto() {}

	/**
	 * @param subject
	 * @param beforeSql
	 * @param runSql
	 * @param sendMethod
	 * @param enableYN
	 * @param schedule
	 * @param databaseId
	 * @param memberId
	 * @param sendMemberIds
	 */
	public IncidentAlarmAddDto(@NotBlank @Length(max = 60) String subject,
			@NotBlank @Length(max = 8000) String beforeSql, @NotBlank @Length(max = 8000) String runSql,
			@NotNull SendMethod sendMethod, @NotNull YN enableYN, @NotBlank String schedule, @NotNull Long databaseId,
			@NotNull Long memberId, @NotNull List<Long> sendMemberIds) {

		this.subject = subject;
		this.beforeSql = beforeSql;
		this.runSql = runSql;
		this.sendMethod = sendMethod;
		this.enableYN = enableYN;
		this.schedule = schedule;
		this.databaseId = databaseId;
		this.memberId = memberId;
		this.sendMemberIds = sendMemberIds;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getBeforeSql() {
		return beforeSql;
	}

	public void setBeforeSql(String beforeSql) {
		this.beforeSql = beforeSql;
	}

	public String getRunSql() {
		return runSql;
	}

	public void setRunSql(String runSql) {
		this.runSql = runSql;
	}

	public SendMethod getSendMethod() {
		return sendMethod;
	}

	public void setSendMethod(SendMethod sendMethod) {
		this.sendMethod = sendMethod;
	}

	public YN getEnableYN() {
		return enableYN;
	}

	public void setEnableYN(YN enableYN) {
		this.enableYN = enableYN;
	}

	public String getSchedule() {
		return schedule;
	}

	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

	public Long getDatabaseId() {
		return databaseId;
	}

	public void setDatabaseId(Long databaseId) {
		this.databaseId = databaseId;
	}

	public Long getMemberId() {
		return memberId;
	}

	public void setMemberId(Long memberId) {
		this.memberId = memberId;
	}

	public List<Long> getSendMemberIds() {
		return sendMemberIds;
	}

	public void setSendMemberIds(List<Long> sendMemberIds) {
		this.sendMemberIds = sendMemberIds;
	}
}