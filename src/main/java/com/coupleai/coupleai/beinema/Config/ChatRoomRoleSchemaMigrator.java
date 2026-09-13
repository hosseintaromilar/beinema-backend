package com.coupleai.coupleai.beinema.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatRoomRoleSchemaMigrator implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(ChatRoomRoleSchemaMigrator.class);

    private final DataSource dataSource;

    public ChatRoomRoleSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "ALTER TABLE chat_room_participants "
                            + "MODIFY COLUMN role VARCHAR(32) NOT NULL"
            );
            statement.execute(
                    "UPDATE chat_room_participants "
                            + "SET role = 'OWNER' "
                            + "WHERE role = 'PARTNER_A'"
            );
            statement.execute(
                    "UPDATE chat_room_participants "
                            + "SET role = 'MEMBER' "
                            + "WHERE role = 'PARTNER_B'"
            );

            log.info("chat_room_participants.role is VARCHAR(32)");
        } catch (Exception exception) {
            log.warn(
                    "Could not migrate chat_room_participants.role: {}",
                    exception.getMessage()
            );
        }
    }
}
