package com.fin.billage.domain.contract.service;

import com.fin.billage.domain.contract.dto.ContractLoanDetailResponseDto;
import com.fin.billage.domain.contract.dto.ContractLoanResponseDto;

import com.fin.billage.domain.contract.entity.Contract;
import com.fin.billage.domain.contract.entity.Transaction;
import com.fin.billage.domain.contract.repository.ContractRepository;
import com.fin.billage.domain.contract.repository.TransactionRepository;
import com.fin.billage.domain.notice.entity.Notice;
import com.fin.billage.domain.notice.repository.NoticeRepository;
import com.fin.billage.domain.user.entity.User;
import com.fin.billage.domain.user.repository.UserRepository;
import com.fin.billage.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;



@Service
@RequiredArgsConstructor
public class ContractLoanService {
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final NoticeRepository noticeRepository;
    private final JwtUtil jwtUtil;

    // 거래내역 계산
    public BigDecimal calculateTransaction(List<BigDecimal> transaction, BigDecimal amount, Float interestRatePercentage) {
        BigDecimal sum = BigDecimal.ZERO;

        for (BigDecimal t : transaction) {
            sum = sum.add(t); // 현재 합계에 t를 더함
        }

        BigDecimal interestRate = BigDecimal.ZERO; // 기본값 0으로 설정
        BigDecimal remainingAmount = BigDecimal.ZERO;
        BigDecimal totalRepaymentAmount = BigDecimal.ZERO;

        if (interestRatePercentage != null && interestRatePercentage != 0) {
            // interestRatePercentage가 null이 아니고 0이 아닌 경우에만 계산
            interestRate = new BigDecimal(Float.toString(interestRatePercentage)).divide(BigDecimal.valueOf(100));
            // 원금에 이자를 더한 총 상환 금액을 계산
            totalRepaymentAmount = amount.add(amount.multiply(interestRate));
        } else {
            totalRepaymentAmount = amount;
        }

        remainingAmount = totalRepaymentAmount.subtract(sum);

        return remainingAmount;
    }

    // 빌려준 거래 목록 리스트
    public List<ContractLoanResponseDto> searchLendList(HttpServletRequest request) {
        Long user_pk = jwtUtil.extractUserPkFromToken(request);
        User user = userRepository.findById(user_pk).orElse(null);

//        Contract contract = contractRepository.findByContractId(contractId);
//        Contract contract = contractRepository.findByContractId(user);
        // 송금인(tran_wd)가 debeter_user인 경우의 tran_amt를 가져와서
        // calculateTransaction(List<Bigdecimal> tran_amt, 빌린금액)에 넣어주기
        List<Contract> contracts = contractRepository.findAllByCreditorUser(user);
        List<ContractLoanResponseDto> lendList = new ArrayList<>();

        for (Contract c : contracts) {
            String tranWd = c.getDebtorUser().getUserName();
//            List<BigDecimal> tranAmtList = transactionRepository.findAllTranAmtByContractAndTranWd(c, tranWd);
            List<Transaction> tranList = transactionRepository.findAllByContractAndTranWd(c, tranWd);
            List<BigDecimal> tranAmtList = new ArrayList<>();

            for (Transaction b : tranList) {
                tranAmtList.add(b.getTranAmt());
            }

            String creditorBankName = "";
            if (c.getContractCreditorBank().equals("004")) {
                creditorBankName = "국민은행";
            } else if (c.getContractCreditorBank().equals("003")) {
                creditorBankName = "기업은행";
            }

            String debtorBankName = "";
            if (c.getContractDebtorBank().equals("004")) {
                debtorBankName = "국민은행";
            } else if (c.getContractDebtorBank().equals("003")) {
                debtorBankName = "기업은행";
            }

            BigDecimal repaymentCash = calculateTransaction(tranAmtList, c.getContractAmt(), c.getContractInterestRate());
            if (repaymentCash.compareTo(BigDecimal.ZERO) <= 0) {
                c.updateContractCompleteState();
                contractRepository.save(c);

                // 차용증 이체 노티에 등록
                Notice n = Notice.builder()
                        .contractId(c.getContractId())
                        .user(c.getCreditorUser())
                        .noticeUserName(c.getDebtorUser().getUserName())
                        .noticeSendDate(LocalDateTime.now())
                        .noticeType(5)
                        .build();

                noticeRepository.save(n);
            }

            ContractLoanResponseDto contractLoanResponseDto = ContractLoanResponseDto.builder()
                    .contractId(c.getContractId())
                    .contractAmt(c.getContractAmt())
                    .contractState(c.getContractState())
                    .creditorUser(c.getCreditorUser())
                    .debtorUser(c.getDebtorUser())
                    .repaymentCash(repaymentCash)
                    .remainingLoanTerm(ChronoUnit.DAYS.between(LocalDate.now(), c.getContractMaturityDate()))
                    .creditorBankCode(c.getContractCreditorBank())
                    .creditorBankName(creditorBankName)
                    .creditorAcNum(c.getContractCreditorAcNum())
                    .debtorBankCode(c.getContractDebtorBank())
                    .debtorBankName(debtorBankName)
                    .debtorAcNum(c.getContractDebtorAcNum())
                    .interestRate(c.getContractInterestRate())
                    .build();

            lendList.add(contractLoanResponseDto);
        }

        return lendList;
    }

    // 빌린 거래 목록 리스트
    public List<ContractLoanResponseDto> searchBorrowList(HttpServletRequest request) {
        Long user_pk = jwtUtil.extractUserPkFromToken(request);
        User user = userRepository.findById(user_pk).orElse(null);

//        Contract contract = contractRepository.findByContractId(contractId);

        // 송금인(tran_wd)가 debeter_user인 경우의 tran_amt를 가져와서
        // calculateTransaction(List<Bigdecimal> tran_amt, 빌린금액)에 넣어주기

        List<Contract> contracts = contractRepository.findAllByDebtorUser(user);
        List<ContractLoanResponseDto> borrowList = new ArrayList<>();


        for (Contract c : contracts) {
            String tranWd = c.getDebtorUser().getUserName();
//            List<BigDecimal> tranAmtList = transactionRepository.findAllTranAmtByContractAndTranWd(c, tranWd);
            List<Transaction> tranList = transactionRepository.findAllByContractAndTranWd(c, tranWd);
            List<BigDecimal> tranAmtList = new ArrayList<>();

            for (Transaction b : tranList) {
                tranAmtList.add(b.getTranAmt());
            }

            String creditorBankName = "";
            if (c.getContractCreditorBank().equals("004")) {
                creditorBankName = "국민은행";
            } else if (c.getContractCreditorBank().equals("003")) {
                creditorBankName = "기업은행";
            }

            String debtorBankName = "";
            if (c.getContractDebtorBank().equals("004")) {
                debtorBankName = "국민은행";
            } else if (c.getContractDebtorBank().equals("003")) {
                debtorBankName = "기업은행";
            }

            BigDecimal repaymentCash = calculateTransaction(tranAmtList, c.getContractAmt(), c.getContractInterestRate());
            if (repaymentCash.compareTo(BigDecimal.ZERO) <= 0) {
                c.updateContractCompleteState();
                contractRepository.save(c);

                // 차용증 이체 노티에 등록
                Notice n = Notice.builder()
                        .contractId(c.getContractId())
                        .user(c.getCreditorUser())
                        .noticeUserName(c.getDebtorUser().getUserName())
                        .noticeSendDate(LocalDateTime.now())
                        .noticeType(5)
                        .build();

                noticeRepository.save(n);
            }

            ContractLoanResponseDto contractLoanResponseDto = ContractLoanResponseDto.builder()
                    .contractId(c.getContractId())
                    .contractAmt(c.getContractAmt())
                    .contractState(c.getContractState())
                    .creditorUser(c.getCreditorUser())
                    .debtorUser(c.getDebtorUser())
                    .repaymentCash(repaymentCash)
                    .remainingLoanTerm(ChronoUnit.DAYS.between(LocalDate.now(), c.getContractMaturityDate()))
                    .creditorBankCode(c.getContractCreditorBank())
                    .creditorBankName(creditorBankName)
                    .creditorAcNum(c.getContractCreditorAcNum())
                    .debtorBankCode(c.getContractDebtorBank())
                    .debtorBankName(debtorBankName)
                    .debtorAcNum(c.getContractDebtorAcNum())
                    .interestRate(c.getContractInterestRate())
                    .build();

            borrowList.add(contractLoanResponseDto);
        }

        return borrowList;
    }

    // 거래 상세
    public ContractLoanDetailResponseDto detailLoan(Long contractId, HttpServletRequest request) {
        Contract contract = contractRepository.findByContractId(contractId);

        String creditorAcNum = "";
        String creditorBank = "";

        if (contract.getContractCreditorAcNum() != null) {
            creditorAcNum = contract.getContractCreditorAcNum();
        }

        if (contract.getContractCreditorBank() != null) {
            creditorBank = (contract.getContractCreditorBank().equals("004")) ? "국민은행" : "기업은행";
        }

        // 송금인(tran_wd)가 debeter_user인 경우의 tran_amt를 가져와서
        // calculateTransaction(List<Bigdecimal> tran_amt, 빌린금액)에 넣어주기
        String tranWd = contract.getDebtorUser().getUserName();
//        List<BigDecimal> tranAmtList = transactionRepository.findAllTranAmtByContractAndTranWd(contract, tranWd);
        List<Transaction> tranList = transactionRepository.findAllByContractAndTranWd(contract, tranWd);
        List<BigDecimal> tranAmtList = new ArrayList<>();

        for (Transaction b : tranList) {
            tranAmtList.add(b.getTranAmt());
        }

        ContractLoanDetailResponseDto contractLoanDetailResponseDto = ContractLoanDetailResponseDto.builder()
                .contractAmt(contract.getContractAmt())
                .contractStartDate(contract.getContractStartDate())
                .contractMaturityDate(contract.getContractMaturityDate())
                .contractInterestRate(contract.getContractInterestRate())
                .repaymentCash(calculateTransaction(tranAmtList, contract.getContractAmt(), contract.getContractInterestRate()))
                .bankCode(contract.getContractCreditorBank())
                .mainAccount(creditorBank + " " + creditorAcNum)
                .build();

        return contractLoanDetailResponseDto;
    }
}
