FROM mysql:8.0

COPY mysqld.my /usr/sbin/mysqld.my
COPY component_keyring_file.cnf /usr/lib64/mysql/plugin/component_keyring_file.cnf
COPY encryption.cnf /etc/mysql/conf.d/encryption.cnf

RUN install -o mysql -g mysql -m 0600 /dev/null /var/lib/mysql-keyring/component_keyring_file \
 && chmod 0644 /usr/sbin/mysqld.my /usr/lib64/mysql/plugin/component_keyring_file.cnf /etc/mysql/conf.d/encryption.cnf

VOLUME /var/lib/mysql-keyring
