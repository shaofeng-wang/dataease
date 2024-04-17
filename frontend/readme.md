### Error
```sh
library: 'digital envelope routines',
  reason: 'unsupported',
  code: 'ERR_OSSL_EVP_UNSUPPORTED'
```
### Solution
```sh
export NODE_OPTIONS=--openssl-legacy-provider
```