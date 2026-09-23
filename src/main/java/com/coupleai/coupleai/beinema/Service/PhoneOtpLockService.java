package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.PhoneLoginCode;
import com.coupleai.coupleai.beinema.Repository.PhoneLoginCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneOtpLockService {

    static final int MAX_ATTEMPTS = 5;

    private final PhoneLoginCodeRepository phoneLoginCodeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedAttempt(Long codeId) {
        PhoneLoginCode stored = phoneLoginCodeRepository.findById(codeId)
                .orElseThrow(() -> new IllegalStateException("کد ورود پیدا نشد"));

        int attempts = stored.getAttempts() + 1;
        stored.setAttempts(attempts);
        if (attempts >= MAX_ATTEMPTS) {
            stored.setUsed(true);
        }
        phoneLoginCodeRepository.save(stored);
        return attempts;
    }
}
