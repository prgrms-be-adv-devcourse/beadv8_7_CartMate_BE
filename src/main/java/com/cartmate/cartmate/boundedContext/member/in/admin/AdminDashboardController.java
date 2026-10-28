package com.cartmate.cartmate.boundedContext.member.in.admin;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.dto.AdminDashboardResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final MemberFacade memberFacade;

    @GetMapping
    @Operation(operationId = "getAdminDashboard", summary = "관리자 대시보드")
    public RsData<AdminDashboardResponse> get() {
        return new RsData<>("200-1", "관리자 대시보드 조회 성공", memberFacade.getAdminDashboard());
    }
}