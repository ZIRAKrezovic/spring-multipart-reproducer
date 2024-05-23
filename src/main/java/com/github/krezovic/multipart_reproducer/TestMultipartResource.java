package com.github.krezovic.multipart_reproducer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping(value = "upload")
public class TestMultipartResource {
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadMultipart(@RequestParam("file") MultipartFile multipartFile) {
        log.debug("Received file");
    }
}
