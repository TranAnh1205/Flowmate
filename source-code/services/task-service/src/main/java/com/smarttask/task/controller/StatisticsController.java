package com.smarttask.task.controller;
import com.smarttask.task.dto.TaskDtos.DashboardResponse; import com.smarttask.task.service.TaskService; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.time.ZoneId; import java.util.UUID;
@RestController @RequestMapping("/api/v1/statistics")
public class StatisticsController{private final TaskService s;public StatisticsController(TaskService s){this.s=s;}@GetMapping("/dashboard") public DashboardResponse dashboard(Authentication a,@RequestParam(defaultValue="Asia/Bangkok")String timezone){return s.dashboard(UUID.fromString(a.getName()),ZoneId.of(timezone));}}

