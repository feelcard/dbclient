package com.song7749.incident.task;

import static com.song7749.util.LogMessageFormatter.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.song7749.base.MessageVo;
import com.song7749.base.SendMethod;
import com.song7749.base.WebSocketMessageVo;
import com.song7749.base.YN;
import com.song7749.dbclient.domain.Member;
import com.song7749.dbclient.service.DBclientManager;
import com.song7749.dbclient.value.ExecuteQueryDto;
import com.song7749.incident.domain.IncidentAlarm;
import com.song7749.incident.repository.IncidentAlarmRepository;
import com.song7749.incident.value.IncidentAlarmVo;
import com.song7749.mail.service.EmailService;
import com.song7749.mail.value.MailMessageVo;

/**
 * <pre>
 * Class Name : IncidentAlarmTask.java
 * Description : 알람 실행
*
*
*  Modification Information
*  Modify Date 		Modifier				Comment
*  -----------------------------------------------
*  2018. 5. 13.		song7749@gmail.com		NEW
*
* </pre>
*
* @author song7749@gmail.com
* @since 2018. 5. 13.
*/
@SuppressWarnings("unchecked")
public class IncidentAlarmTask implements Runnable {

	Logger logger = LoggerFactory.getLogger(getClass());

	private DBclientManager dbClientManager;

	private IncidentAlarm incidentAlarm;

	private IncidentAlarmRepository incidentAlarmRepository;

	private EmailService emailService;

	private SimpMessagingTemplate template;

	ModelMapper mapper;

	public IncidentAlarmTask(DBclientManager dbClientManager
			, IncidentAlarm incidentAlarm
			, IncidentAlarmRepository incidentAlarmRepository
			, EmailService emailService
			, SimpMessagingTemplate template
			, ModelMapper mapper) {

		this.dbClientManager=dbClientManager;
		this.incidentAlarm=incidentAlarm;
		this.incidentAlarmRepository=incidentAlarmRepository;
		this.emailService=emailService;
		this.template=template;
		this.mapper=mapper;
	}

	public IncidentAlarm getIncidentAlarm() {
		return incidentAlarm;
	}

	@Override
	public void run() {
		logger.trace(format("{}", "TASK RUN START"),incidentAlarm.getId());
		Long startTime = System.currentTimeMillis();

		// 실행 상태
		 boolean isExecute = YN.Y.equals(incidentAlarm.getEnableYN());
		// 승인 상태
		isExecute = isExecute && YN.Y.equals(incidentAlarm.getConfirmYN());

		// 전송 대상자가 있는가?
		isExecute= isExecute
				&& null!=incidentAlarm.getSendMembers()
				&& !incidentAlarm.getSendMembers().isEmpty();

		// 테스트 인 경우에만 허용 한다.
		isExecute = isExecute || incidentAlarm.isTest();

		// 실행 불가능 상태인 경우에는 에러를 내보낸다.
		if(isExecute==false) {
			try {
				throw new IllegalArgumentException("실행 실패. 실행가능 상태가 아니거나, 승인되지 않았거나, 전송대상자 없음.");
			} catch (Exception e) {
				logger.error(e.getMessage());
				incidentAlarm.setLastErrorMessage(e.getMessage());
				incidentAlarmRepository.saveAndFlush(incidentAlarm);
			}
		}


		ExecuteQueryDto dto = new ExecuteQueryDto();
		dto.setId(incidentAlarm.getDatabase().getId());
		dto.setLoginId(incidentAlarm.getResistMember().getLoginId());
		dto.setIp("ALARM-SYSTEM");
		dto.setUseLimit(false);
		dto.setUseCache(false);

		// 전송 해야하는 상태인가 확인 한다.
		if(isExecute) {
			dto.setQuery(incidentAlarm.getBeforeSql());

			try {
				MessageVo vo = dbClientManager.executeQuery(dto);
				List<Map<String,String>> contents = (List<Map<String, String>>) vo.getContents();
				if(null!=contents && contents.size()>0) {
					for(String  key : contents.get(0).keySet()) {
						if("Y".equals(contents.get(0).get(key))){
							isExecute=true;
							break;
						}
					}
				} else {
					throw new IllegalArgumentException("before sql 이 올바르지 않습니다. SQL : " + incidentAlarm.getBeforeSql());
				}
			} catch (Exception e) {
				isExecute=false; // 실행 중지
				incidentAlarm.setLastErrorMessage(e.getMessage());
			}
		}

		// 전송해야 하는 데이터
		if(isExecute) {
			try {
				// email 전송
				if(SendMethod.EMAIL.equals(incidentAlarm.getSendMethod())) {
					String emailContents=null;
					String fileName=null;
					// 다중 SQL 인 경우에는 컨텐츠 생성.
					if(incidentAlarm.getRunSql().toLowerCase().indexOf("<sql>") >=0) {
						emailContents = makeEmailContents(dto);
					} else { // 싱글 SQL 인 경우
						// contents 의 길이가 길면 엑셀로 만들어서 첨부해야 한다.
						dto.setQuery(incidentAlarm.getRunSql());
						MessageVo vo = dbClientManager.executeQuery(dto);
						// size 가 긴 경우 엑셀로 만들어서 첨부한다.
						if(null!=vo.getContents()
								&& vo.getContents() instanceof List
								&& ((List)vo.getContents()).size() > 0) {

					        // 워크북 생성
					        HSSFWorkbook workbook = new HSSFWorkbook();
					        // 워크시트 생성
					        HSSFSheet sheet = workbook.createSheet();
					        // 행 생성
					        HSSFRow row;
					        // 쎌 생성
					        HSSFCell cell;
					        // 엑셀 생성 시작
					        int listLoop=0;
					        int mapLoop=0;
					        for(Map data : (List<Map<String, String>>)vo.getContents()) {
					        	// 첫번째 loop 인 경우에는 header 를 만든다.
					        	if(listLoop==0) {
				        			row = sheet.createRow(listLoop);
				        			for(String key : ((Map<String, String>)data).keySet()) {
					        			cell = row.createCell(mapLoop);
				        		        cell.setCellValue(key);
						        		mapLoop++;
						        	}
				        			mapLoop=0;
					        	}
					        	// 그 외에는 body 를 만든다.
			        			row = sheet.createRow(listLoop+1);
			        			for(String key : ((Map<String, String>)data).keySet()) {
			        				cell = row.createCell(mapLoop);
				        		    cell.setCellValue((String)data.get(key));
			        				sheet.autoSizeColumn(mapLoop);
				        		    mapLoop++;
					        	}
					        	listLoop++;
					        	mapLoop=0;
					        }
							// 엑셀 파일을 생성하여 저장 한다.
							fileName=System.getProperty("java.io.tmpdir")+"/"+incidentAlarm.getId() + "mail_contents.xls";
							File file = new File(fileName);
							FileOutputStream fos = null;
							try {
								fos = new FileOutputStream(file);
								workbook.write(fos);
							} catch (FileNotFoundException e) {
								throw e;
							} catch (IOException e) {
								throw e;
							} finally {
								try {
									if(workbook!=null) workbook.close();
									if(fos!=null) fos.close();
								} catch (IOException e) {
									throw e;
								}
							}
						} else { // 짧은 경우에는  내용으로 첨부 한다.
							emailContents = generateEmailHtml((List<Map<String, String>>) vo.getContents());
						}
					}
					sendEmailMessage(emailContents, fileName);
					// 메일 전송 후 파일 삭제
					// 삭제할 필요가 있을까 고민 필요.. 계속 쓸테니..
					File file = new File(fileName);
					if(file.exists()) {
						file.delete();
					}
				}
			} catch (Exception e) {
				isExecute=false; // 실행 중지
				logger.error(e.getMessage());
				incidentAlarm.setLastErrorMessage(e.getMessage());
				incidentAlarmRepository.saveAndFlush(incidentAlarm);
			}
		}

		// 전송완료 후 기록
		if(isExecute) {
			incidentAlarm.setLastErrorMessage("");
			incidentAlarm.setLastRunDate(new Date(System.currentTimeMillis()));
			incidentAlarmRepository.saveAndFlush(incidentAlarm);
		}
		logger.trace(format("{}", "TASK RUN END"),incidentAlarm.getId());

		try {
			WebSocketMessageVo sendMessage = new WebSocketMessageVo();
			sendMessage.setHttpStatus(isExecute ? HttpStatus.OK.value() : HttpStatus.INTERNAL_SERVER_ERROR.value());
			sendMessage.setMessage("Task Execute Run Finish");
			sendMessage.setContents(mapper.map(incidentAlarm, IncidentAlarmVo.class));
			sendMessage.setProcessTime(System.currentTimeMillis()-startTime);
			template.convertAndSend("/topic/runAlarms", sendMessage);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	/**
	 * 메일 내용을 생성 한다.
	 * @param dto
	 * @return String
	 */
	private String makeEmailContents(ExecuteQueryDto dto) {
		String sendEmailContents = "";
		// 다중 발송인가 확인 필요 <sql> 테그가 있으면 다중 발송이다. 다중 발송일 경우에는 엑셀 첨부를 지원하지 않는다.
		if(incidentAlarm.getRunSql().toLowerCase().indexOf("<sql>") >=0) {
			// 쓰기 테그와 닫기테그가 같은 숫자인지 확인한다.
			int openTag = StringUtils.countMatches(incidentAlarm.getRunSql(), "<sql>");
			int closeTag = StringUtils.countMatches(incidentAlarm.getRunSql(), "</sql>");
			if(openTag != closeTag) {
				throw new IllegalArgumentException("SQL 테그의 열기테그와 닫기 테그의 숫자가 일치하지 않습니다");
			}

			// 테그의 숫자 만큼 수행 한다. -- SQL 뽑아내기
			String contents = incidentAlarm.getRunSql();
			Map<String,String> sqlMap = new HashMap<String,String>();
			for(int i=0;i<openTag;i++) {
				// 해당 테그의 시작과 끝을 뽑아온다.
				int startIndex=contents.toLowerCase().indexOf("<sql>");
				int endIndex=contents.toLowerCase().indexOf("</sql>") + "</sql>".length();
				if(startIndex>=0) {
					String sql = contents.substring(startIndex, endIndex);
					contents=contents.replace(sql, "#^#"+i);
//					logger.trace(format("{}", "email replace sql"),sql);
//					logger.trace(format("{}", "email replace contents"),contents);
					sqlMap.put("#^#"+i, sql.toLowerCase().replace("<sql>", "").replace("</sql>", ""));
				}
			}

			// 쿼리를 실행해서 html 을 생성 한다.
			for(String key : sqlMap.keySet()) {
				dto.setQuery(sqlMap.get(key));
				MessageVo vo = dbClientManager.executeQuery(dto);
				String html = generateEmailHtml((List<Map<String, String>>) vo.getContents());
				contents=contents.replace(key, html);
			}
			sendEmailContents=contents;
//			logger.trace(format("{}", "email Send"),sendEmailContents);
//			throw new IllegalArgumentException("실행중지");
		}
		return sendEmailContents;
	}

	private void sendEmailMessage(String sendEmailContents, String fileName) {

		logger.info(format("{}", "SEND Email"),incidentAlarm.getId());
		// 메세지 생성
		StringBuffer sendMessageBuffer = new StringBuffer();
		sendMessageBuffer.append("<style type=\"text/css\">");
		sendMessageBuffer.append(".tg  {border-collapse:collapse;border-spacing:0;}");
		sendMessageBuffer.append(".tg td{font-family:Arial, sans-serif;font-size:14px;padding:10px 5px;border-style:solid;border-width:1px;overflow:hidden;word-break:normal;border-color:black;}");
		sendMessageBuffer.append(".tg th{font-family:Arial, sans-serif;font-size:14px;font-weight:normal;padding:10px 5px;border-style:solid;border-width:1px;overflow:hidden;word-break:normal;border-color:black;}");
		sendMessageBuffer.append(".tg .tg-us36{border-color:inherit;vertical-align:top}");
		sendMessageBuffer.append(".tg .tg-3mv2{background-color:#96fffb;border-color:inherit;vertical-align:top}");
		sendMessageBuffer.append("</style>");

		// 메일 내용이 있는 경우에는 추가 한다.
		if(StringUtils.isNotBlank(incidentAlarm.getSendMessage())) {
			logger.trace(format("{}", "Alarm Send Message"),incidentAlarm.getSendMessage());
			sendMessageBuffer.append(incidentAlarm.getSendMessage().replace("\n", "<br/>"));
		}

		// 메일 contents 추가
		if(StringUtils.isNotBlank(sendEmailContents)){
			sendMessageBuffer.append(sendEmailContents.replace("\n", "<br/>"));
		}

		// 전송 대상자 포멧 변경
		List<String> to = new ArrayList<String>();
		// 테스트 인 경우 본인에게만 전송
		if(incidentAlarm.isTest()) {
				to.add(incidentAlarm.getTestSendMember().getLoginId());
 		} else { // 테스트가 아닌 경우 수신자에게 전송
 			for(Member m : incidentAlarm.getSendMembers()) {
 				to.add(m.getLoginId());
 			}
 		}

		// 메일 메세지 생성
		MailMessageVo vo = new MailMessageVo(incidentAlarm.getResistMember().getLoginId()
				, to
				, null
				, null
				, incidentAlarm.getSubject()
				, sendMessageBuffer.toString()
				, null);

		// 파일이 있는 경우
		if(StringUtils.isNotBlank(fileName)) {
			vo.setFiles(Arrays.asList(new String[] {fileName}));
		}

		// 메일 전송
		try {
			emailService.sendMessage(vo);
		} catch (MessagingException e) {
			logger.error(e.getMessage());
			incidentAlarm.setLastErrorMessage(e.getMessage());
		}
	}

	private String generateEmailHtml(List<Map<String,String>> contents) {
		StringBuffer sendMessageBuffer = new StringBuffer();
		if(null!=contents) {
			sendMessageBuffer.append("<table class=\"tg\">");
			for(int i=0; i < contents.size(); i++) {
				// table head 만들기
				if(i==0) {
					sendMessageBuffer.append("<thead>");
					sendMessageBuffer.append("<tr>");
					for(String head : contents.get(i).keySet()) {
						sendMessageBuffer.append("<th class=\"tg-3mv2\">");
						sendMessageBuffer.append(head);
						sendMessageBuffer.append("</th>");
					}
					sendMessageBuffer.append("</tr>");
					sendMessageBuffer.append("</thead>");
				} else {
					sendMessageBuffer.append("<tr>");
					for(String head : contents.get(i).keySet()) {
						sendMessageBuffer.append("<td class=\"tg-us36\">");
						sendMessageBuffer.append(contents.get(i).get(head));
						sendMessageBuffer.append("</td>");
					}
					sendMessageBuffer.append("</tr>");
				}
			}
			sendMessageBuffer.append("</table>");
		} else {
			sendMessageBuffer.append("<table class=\"tg\"><tr><td class=\"tg-us36\">데이터가 없습니다</td></tr></table>");
		}
		return sendMessageBuffer.toString();
	}
}