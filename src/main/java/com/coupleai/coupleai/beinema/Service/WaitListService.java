package com.coupleai.coupleai.beinema.Service;

import com.coupleai.coupleai.beinema.Entity.WaitListEntity;
import com.coupleai.coupleai.beinema.Exception.InvalidContactException;
import com.coupleai.coupleai.beinema.Repository.WaitListRepository;
import com.coupleai.coupleai.beinema.DTO.WaitList.WaitListRequest;
import com.coupleai.coupleai.beinema.DTO.WaitList.WaitListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WaitListService {

    private final WaitListRepository waitlistRepository;

    public WaitListResponse join(WaitListRequest request) {

        String contact = request.getContact();

        WaitListEntity entity = WaitListEntity.builder()
                .build();


        if (isEmail(contact)) {

            if (waitlistRepository.existsByEmail(contact)) {
                throw new InvalidContactException("این ایمیل قبلاً در لیست انتظار ثبت شده است.");
            }

            entity.setEmail(contact);

        }
        else if (isPhoneNumber(contact)) {

            if (waitlistRepository.existsByPhoneNumber(contact)) {
                throw new InvalidContactException("این شماره موبایل قبلاً در لیست انتظار ثبت شده است.");
            }

            entity.setPhoneNumber(contact);

        }
        else {

            throw new InvalidContactException("فرمت ایمیل یا شماره موبایل صحیح نیست.");

        }


        waitlistRepository.save(entity);
        return new WaitListResponse(
                "شما با موفقیت به لیست انتظار بین‌ما اضافه شدید."
        );
    }


    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^09[0-9]{9}$");

    private boolean isEmail(String value) {
        return EMAIL_PATTERN.matcher(value).matches();
    }

    private boolean isPhoneNumber(String value) {
        return PHONE_PATTERN.matcher(value).matches();
    }

}
