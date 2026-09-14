# Listas de contraseñas prohibidas

`ncsc-100k-common.txt` contiene, en minúsculas y sin duplicados, las contraseñas de 8 o más caracteres de
[`100k-most-used-passwords-NCSC.txt`](https://github.com/danielmiessler/SecLists/blob/master/Passwords/Common-Credentials/100k-most-used-passwords-NCSC.txt)
de SecLists (licencia MIT, © Daniel Miessler). Las más cortas no hacen falta: la política ya las rechaza por longitud.

Para regenerarla:

```bash
curl -sL https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Common-Credentials/100k-most-used-passwords-NCSC.txt \
  | tr -d '\r' | awk 'length($0) >= 8' | tr '[:upper:]' '[:lower:]' | LC_ALL=C sort -u > ncsc-100k-common.txt
```
