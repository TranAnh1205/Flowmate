package com.smarttask.task.exception;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.http.*; import org.springframework.http.converter.HttpMessageNotReadableException; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException; import java.time.*; import java.util.*;
@RestControllerAdvice
public class GlobalExceptionHandler{
 public record ErrorResponse(Instant timestamp,int status,String error,String code,String message,String path,Map<String,String> details){}
 @ExceptionHandler(TaskApiException.class) ResponseEntity<ErrorResponse> api(TaskApiException e,HttpServletRequest r){return out(e.status(),e.code(),e.getMessage(),r.getRequestURI(),Map.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e,HttpServletRequest r){Map<String,String>d=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(x->d.putIfAbsent(x.getField(),x.getDefaultMessage()));return out(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Dữ liệu đầu vào không hợp lệ",r.getRequestURI(),d);}
 @ExceptionHandler({DateTimeException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<ErrorResponse> badRequest(Exception e,HttpServletRequest r){return out(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Tham số hoặc dữ liệu đầu vào không hợp lệ",r.getRequestURI(),Map.of());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException e,HttpServletRequest r){return out(HttpStatus.CONFLICT,"DATA_CONFLICT","Dữ liệu đã tồn tại hoặc đang được sử dụng",r.getRequestURI(),Map.of());}
 private ResponseEntity<ErrorResponse> out(HttpStatus s,String c,String m,String p,Map<String,String>d){return ResponseEntity.status(s).body(new ErrorResponse(Instant.now(),s.value(),s.getReasonPhrase(),c,m,p,d));}
}
