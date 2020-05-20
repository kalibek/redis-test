package com.epam;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;


@Data
class TaskToCompleteDTO {

  private String requestId;
  private int taskToComplete;
}

@Component
@Slf4j
class MessageConsumer {

  @KafkaListener(topics = "messages", concurrency = "4")
  void onMessage(TaskToCompleteDTO dto) throws InterruptedException {
    Jedis client = new Jedis();
    client.hset(dto.getRequestId(), "flag_" + dto.getTaskToComplete(), "1");
    Thread.sleep(1000);
    Map<String, String> flags = client.hgetAll(dto.getRequestId());
    boolean completed = flags.entrySet().stream().allMatch(e -> "1".equals(e.getValue()));
    if (completed) {
      log.info("completed request {} in stream {} [{}]", dto.getRequestId(),
          dto.getTaskToComplete(), Thread.currentThread().toString());
      Long hdel = client.hdel(dto.getRequestId(),
          "flag_0",
          "flag_1",
          "flag_2",
          "flag_3",
          "flag_4",
          "flag_5",
          "flag_6",
          "flag_7");
      log.info("deletion result is {}", hdel);
      if (hdel != 0) {
        log.info("WE SENDING MESSAGE ONCE!!!!!!!!");
      }
    }
    log.info("stream {} updated flags {}", dto.getRequestId(), flags);
  }

}


@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class RedisTransactionsApplication implements CommandLineRunner {

  private final KafkaTemplate<String, TaskToCompleteDTO> kafkaTemplate;

  public static void main(String[] args) {
    SpringApplication.run(RedisTransactionsApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    String requestId = "flags#" + "somekey";
    Jedis jedis = new Jedis();
    for (int i = 0; i < 8; i++) {
      jedis.hset(requestId, "flag_" + i, "0");
    }

    ExecutorService executorService = Executors.newFixedThreadPool(8);
    for (int i = 0; i < 8; i++) {
      executorService.submit(getLambda(requestId, i));
    }
  }

  private Runnable getLambda(String requestId, int taskToComplete) {
    return () -> {
      try {
        int millis = 1000; // ThreadLocalRandom.current().nextInt(1000, 10000);
        log.info("thread {} sleeps for {}", taskToComplete, millis);
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        log.error("cannot sleep", e);
      }
      TaskToCompleteDTO data = new TaskToCompleteDTO();
      data.setRequestId(requestId);
      data.setTaskToComplete(taskToComplete);
      log.info("sending data {}", data);
      kafkaTemplate.send("messages", taskToComplete % 4, requestId, data);

    };
  }
}
