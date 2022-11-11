package com.sanwar.reactive.controller;

import java.util.HashMap;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sanwar.reactive.dto.GeneralResponse;
import com.sanwar.reactive.model.Students;
import com.sanwar.reactive.repository.CourseWorkRepository;
import com.sanwar.reactive.repository.StudentsRepository;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class StudentsController {

    @Autowired
    StudentsRepository studentsRepository;
    @Autowired
    CourseWorkRepository courseWorkRepository;

    @GetMapping("/students/{studentID}")
    public Mono<ResponseEntity<Students>> getStudent(@PathVariable Long studentID) {
        return studentsRepository.findById(studentID).map(student -> {
            return new ResponseEntity<>(student, HttpStatus.OK);
        });
    }

    @PostMapping("/students")
    public Mono<ResponseEntity<Students>> addStudent(@RequestBody Students studentAdd) {
        studentAdd.setRegisteredOn(System.currentTimeMillis());
        studentAdd.setStatus(1);
        return studentsRepository.save(studentAdd).map(student -> {
            return new ResponseEntity<>(student, HttpStatus.CREATED);
        });
    }

    @PutMapping("/students/{studentID}")
    public Mono<ResponseEntity<GeneralResponse<Students>>> updateStudent(@PathVariable Long studentID,
                                                                         @RequestBody Students newStudentData) {
        return studentsRepository.findById(studentID)
                                 .switchIfEmpty(Mono.error(
                                         new Exception(String.format("Student with ID %d not found", studentID))))
                                 .flatMap(foundStudent -> {
                                     //here we are just updating the name. You can add others
                                     foundStudent.setName(newStudentData.getName());
                                     return studentsRepository.save(foundStudent);
                                 })
                                 .map(student -> {
                                     HashMap<String, Students> data = new HashMap<>();
                                     data.put("student", student);
                                     return new ResponseEntity<>(GeneralResponse.<Students>builder().success(true)
                                                                                                    .message(
                                                                                                            "Student update successfully")
                                                                                                    .data(data)
                                                                                                    .build(),
                                             HttpStatus.ACCEPTED);
                                 })
                                 .onErrorResume(e -> {
                                     return Mono.just(new ResponseEntity<>(
                                             GeneralResponse.<Students>builder().success(false)
                                                                                .message(e.getMessage())
                                                                                .build(), HttpStatus.NOT_FOUND));
                                 });
    }

    @DeleteMapping("/students/{studentID}")
    @Transactional
    public Mono<ResponseEntity<GeneralResponse<Students>>> deleteStudent(@PathVariable Long studentID) {
        return studentsRepository.findById(studentID)
                                 .switchIfEmpty(Mono.error(
                                         new Exception(String.format("Student with ID %d not found", studentID))))
                                 .flatMap(foundStudent -> {
                                     return courseWorkRepository.deleteByStudentID(studentID)
                                                                .then(studentsRepository.deleteById(studentID))
                                                                .thenReturn(foundStudent);
                                 })
                                 .map(deletedStudent -> {
                                     HashMap<String, Students> data = new HashMap<>();
                                     data.put("student", deletedStudent);

                                     return new ResponseEntity<>(GeneralResponse.<Students>builder().success(true)
                                                                                                    .message(
                                                                                                            "Student deleted successfully")
                                                                                                    .data(data)
                                                                                                    .build(),
                                             HttpStatus.ACCEPTED);
                                 })
                                 .onErrorResume(e -> {
                                     return Mono.just(new ResponseEntity<>(
                                             GeneralResponse.<Students>builder().success(false)
                                                                                .message(e.getMessage())
                                                                                .build(), HttpStatus.NOT_FOUND));
                                 });
    }

    @GetMapping(value = "/students", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Students> getStudents(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                      @RequestParam(value = "limit", defaultValue = "10") Long limit,
                                      @RequestParam Map<String, String> filterParams) {
        String status = filterParams.getOrDefault("status", null);
        String name = filterParams.getOrDefault("name", null);
        if (name != null) {
            name = "%" + name + "%";
        }
        long offset = (page - 1) * limit;
        return studentsRepository.findAllByStatusAndName(offset, limit, status, name);
    }

}
