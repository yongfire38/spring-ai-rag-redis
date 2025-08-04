package com.example.chat.service;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 기술 정보 JSON 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnologyResponse {
    private String name;
    private String category;
    private String description;
    private List<String> features;
    private List<String> useCases;
    private String complexity;
} 