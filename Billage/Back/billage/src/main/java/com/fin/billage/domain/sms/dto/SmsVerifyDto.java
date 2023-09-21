package com.fin.billage.domain.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class SmsVerifyDto {
    private String phoneNumber;
    private String verifyNumber;
}

