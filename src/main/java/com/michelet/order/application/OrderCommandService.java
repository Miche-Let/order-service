package com.michelet.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderCommandService {

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Order Command Service is Healthy";
    }
}