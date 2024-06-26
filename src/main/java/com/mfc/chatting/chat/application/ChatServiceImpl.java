package com.mfc.chatting.chat.application;

import static com.mfc.chatting.common.response.BaseResponseStatus.*;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfc.chatting.chat.domain.Card;
import com.mfc.chatting.chat.domain.ChatRoom;
import com.mfc.chatting.chat.domain.Member;
import com.mfc.chatting.chat.domain.Message;
import com.mfc.chatting.chat.dto.req.ChatReqDto;
import com.mfc.chatting.chat.dto.resp.ChatPageRespDto;
import com.mfc.chatting.chat.infrastructure.ChatReactiveRepository;
import com.mfc.chatting.chat.infrastructure.ChatRepository;
import com.mfc.chatting.chat.infrastructure.ChatRoomRepository;
import com.mfc.chatting.common.exception.BaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService{
	private final ChatReactiveRepository chatReactiveRepository;
	private final ChatRepository chatRepository;
	private final ObjectMapper objectMapper;
	private final ChatRoomRepository chatRoomRepository;

	@Override
	public Flux<Message> getChatByStream(String roomId, String uuid, Instant now) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndMemberId(roomId, uuid)
				.orElseThrow(() -> new BaseException(CHATROOM_NOT_FOUND));

		return chatReactiveRepository.findChatByRoomId(roomId, Instant.now())
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public ChatPageRespDto getChatByPage(String roomId, String uuid, Pageable page) {
		ChatRoom chatRoom = chatRoomRepository.findByIdAndMemberId(roomId, uuid)
				.orElseThrow(() -> new BaseException(CHATROOM_NOT_FOUND));

		Slice<Message> chats = chatRepository.findByRoomIdAndCreatedAtBefore(
				roomId, findEnterTimeByUuid(chatRoom.getMembers(), uuid), page);

		List<Message> content = chats.getContent();
		System.out.println(content.size());

		return ChatPageRespDto.builder()
				.chats(content)
				.last(chats.isLast())
				.build();
	}

	@Override
	public Mono<Message> sendChat(ChatReqDto dto, String uuid) {
		ChatRoom chatRoom = chatRoomRepository.findById(dto.getRoomId())
				.orElseThrow(() -> new BaseException(CHATROOM_NOT_FOUND));

		String type = dto.getType();
		Card card = null;
		String msg = null;

		if (type.equals("card")) {
			card = objectMapper.convertValue(dto.getMsg(), Card.class);
		} else {
			msg = (String) dto.getMsg();
		}

		List<Member> members = chatRoom.getMembers().stream()
				.map(member -> {
					if(!member.getMemberId().equals(uuid) && member.isEnterTimeBeforeExitTime()) {
						member = Member.builder()
								.memberId(member.getMemberId())
								.unreadCount(member.getUnreadCount() + 1)
								.exitTime(member.getExitTime())
								.enterTime(member.getEnterTime())
								.build();
					}
					return member;
				})
				.toList();

		chatRoomRepository.save(ChatRoom.builder()
				.id(chatRoom.getId())
				.requestId(chatRoom.getRequestId())
				.members(members)
				.createdAt(chatRoom.getCreatedAt())
				.build());

		return chatReactiveRepository.save(Message.builder()
			.type(type)
			.msg(card == null ? msg : card)
			.sender(uuid)
			.roomId(dto.getRoomId())
			.build());
	}

	private Instant findEnterTimeByUuid(List<Member> members, String uuid) {
		return members.stream()
				.filter(m -> m.getMemberId().equals(uuid))
				.findFirst()
				.orElseThrow(() -> new BaseException(INVALID_EXIT))
				.getEnterTime();
	}
}
