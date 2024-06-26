package com.mfc.chatting.chat.presentation;

import java.time.Instant;

import org.apache.kafka.streams.kstream.internals.graph.BaseRepartitionNode;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfc.chatting.chat.application.ChatService;
import com.mfc.chatting.chat.domain.Message;
import com.mfc.chatting.chat.dto.req.ChatReqDto;
import com.mfc.chatting.chat.vo.req.ChatReqVo;
import com.mfc.chatting.chat.vo.resp.ChatPageRespVo;
import com.mfc.chatting.common.response.BaseResponse;
import com.mfc.chatting.common.response.BaseResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@RestController
@RequestMapping("/chat")
@Tag(name = "chat", description = "채팅 메세지 서비스 컨트롤러")
public class ChatController {
	private final ChatService chatService;
	private final ModelMapper modelMapper;

	@GetMapping(value = "/stream/{roomId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "실시간 채팅 조회 API", description = "채팅방 번호에 따른 채팅 목록 조회 (동작 안함 ㅎㅎ..)")
	public Flux<Message> getChatByStream(
			@PathVariable(value ="roomId") String roomId,
			@RequestHeader(value = "UUID", defaultValue = "") String uuid){
		return chatService.getChatByStream(roomId, uuid, Instant.now());
	}

	@GetMapping("/page/{roomId}")
	@Operation(summary = "페이징 채팅 조회 API", description = "채팅방 번호에 따른 채팅 목록 조회")
	public BaseResponse<ChatPageRespVo> getChatByPage(
			@PathVariable String roomId,
			@RequestHeader(value = "UUID", defaultValue = "") String uuid,
			@PageableDefault(size = 3, sort = "createdAt", direction = Sort.Direction.DESC) Pageable page) {
		return new BaseResponse<>(modelMapper.map(
				chatService.getChatByPage(roomId, uuid, page), ChatPageRespVo.class));
	}

	@PostMapping
	@Operation(summary = "채팅 전송 API", description = "type = msg/card/image")
	public Mono<Message> sendChat(@RequestBody ChatReqVo vo,
			@RequestHeader(value = "UUID", defaultValue = "") String uuid){
		return chatService.sendChat(modelMapper.map(vo, ChatReqDto.class),uuid);
	}
}
