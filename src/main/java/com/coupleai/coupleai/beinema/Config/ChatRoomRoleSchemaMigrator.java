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
            statement.execute(
                    "ALTER TABLE messages "
                            + "MODIFY COLUMN sender_type VARCHAR(32) NOT NULL"
            );

            log.info("chat_room_participants.role and messages.sender_type are VARCHAR(32)");
        } catch (Exception exception) {
            log.warn(
                    "Could not migrate chat_room_participants.role: {}",
                    exception.getMessage()
            );
        }

        // a database made by a newer Hibernate has an ENUM here, which rejects SESSION_FEE and CARD_TOP_UP
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE wallet_transactions "
                            + "MODIFY COLUMN type VARCHAR(32) NOT NULL"
            );
            log.info("wallet_transactions.type is VARCHAR(32)");
        } catch (Exception exception) {
            log.warn("Could not migrate wallet_transactions.type: {}", exception.getMessage());
        }

        // an invitation link the owner shares has no phone number (the column was NOT NULL)
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE chat_room_invitations "
                            + "MODIFY COLUMN phone_number VARCHAR(20) NULL"
            );
            log.info("chat_room_invitations.phone_number may be empty");
        } catch (Exception exception) {
            log.warn("Could not migrate chat_room_invitations.phone_number: {}", exception.getMessage());
        }
    }
}
