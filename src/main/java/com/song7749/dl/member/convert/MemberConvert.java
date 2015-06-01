package com.song7749.dl.member.convert;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.song7749.dl.member.dto.AddMemberDTO;
import com.song7749.dl.member.entities.Member;
import com.song7749.dl.member.type.AuthType;
import com.song7749.dl.member.vo.MemberVO;

/**
 * <pre>
 * Class Name : MemberConvert.java
 * Description : Member Entity 를 VO, DTO 등으로 변경 하거나
 * VO,DTO 를 Member Entity 로 변경한다.
 *
 *  Modification Information
 *  Modify Date 		Modifier	Comment
 *  -----------------------------------------------
 *  2015. 4. 29.		song7749	신규작성
 *
 * </pre>
 *
 * @author song7749
 * @since 2015. 4. 29.
 */
public class MemberConvert {

	/**
	 * addMemberDTO to Member
	 * @param dto
	 * @return Member
	 */
	public static Member convert(AddMemberDTO dto){
		if(null==dto){
			return null;
		} else {
			Member member = new Member(dto.getId()
					, dto.getPassword()
					, dto.getEmail()
					, dto.getPasswordQuestion()
					, dto.getPasswordAnswer());
			// 가입 시에 회원 권한을 넣지 않고, 추후 승인된 회원만 권한을 넣어준다.
			return member;
		}
	}

	/**
	 * Member to MemberVO
	 * @param member
	 * @return MemberVO
	 */
	public static MemberVO convert(Member member){
		if(null == member){
			return null;
		} else {
			// 권한 데이터가 있는 경우 -- TODO 추후 1:N 구조가 가능하도록 고쳐야 한다.
			AuthType type = CollectionUtils.isEmpty(member.getMemberAuthList())
					? null : member.getMemberAuthList().get(0).getAuthType();

			return new MemberVO(member.getId()
					, member.getEmail()
					, member.getPasswordQuestion()
					, type);
		}
	}

	/**
	 * Member List to MemberVO List
	 * @param memberList
	 * @return List<MemberVO>
	 */
	public static List<MemberVO> convert(List<Member> memberList){
		if(CollectionUtils.isEmpty(memberList)){
			return null;
		} else {
			List<MemberVO> list = new ArrayList<MemberVO>();
			for(Member m : memberList){
				list.add(convert(m));
			}
			return list;
		}
	}
}