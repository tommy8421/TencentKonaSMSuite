# Tencent Kona Crypto

## Introduction
Tencent Kona Crypto is a Java security provider, which is named `KonaCrypto`. Per the associated China's specifications, it implements the following ShangMi algorithms:

- SM2, which is [Elliptic Curve Cryptography (ECC)]-based public key algorithm. It complies with the below national specifications:
  - GB/T 32918.1-2016 Part 1：General
  - GB/T 32918.2-2016 Part 2：Digital signature algorithm
  - GB/T 32918.3-2016 Part 3：Key exchange protocol
  - GB/T 32918.4-2016 Part 4：Public key encryption algorithm
  - GB/T 32918.5-2017 Part 5：Parameter definition
- SM3, which is a cryptographic hash algorithm. It complies with the below national specification:
  - GB/T 32905-2016 SM3 cryptographic hash algorithm
- SM4, which is a block encryption algorithm. It complies with the below national specification:
  - GB/T 32907-2016 SM4 block cipher algorithm

For providing the above features, `KonaCrypto` implements the JDK-specified Service Provider Interfaces (SPIs), such as KeyPairGeneratorSpi，SignatureSpi，CipherSpi，MessageDigestSpi，MacSpi and KeyAgreementSpi.

## Usages
Now that `KonaCrypto` is based on JCA framework, then the usages are the same as other JCA implementations, such as [SunJCE] and [SunEC]. Understanding the design and coding style on JCA really helps for applying `KonaCrypto`, please read the official [JCA reference].

### Loading
Before using any feature in `KonaCrypto`, it has to load `KonaCryptoProvider`.

```
Security.addProvider(new KonaCryptoProvider());
```

The above line adds this provider at the bottom of the provider list. That means its privilege is the lowest. If necessary, it can insert the provider at specific positions, like the below, 

```
Security.insertProviderAt(new KonaCryptoProvider(), position);
```

the less the position is, the higher the priority is. The minimum value is 1.

### SM2

#### Key pair
Generating SM2 key pair is the same as generating the key pairs on other EC curves. It just needs to invoke the standard JDK APIs.

Create KeyPairGenerator instance.

```
KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("SM2");
```

Generate key pair.

```
KeyPair keyPair = keyPairGenerator.generateKeyPair();
ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
```

SM2 key pair is also EC key pair, so the public key and private key are also ECPublicKey and ECPrivateKey respectively.

SM2 public key is 65-bytes length. The format is `04|x|y`. `04` represents the uncompressed format; `x` and `y` are the coordinates of the public point in the curve.

```
byte[] encodedPublicKey = publicKey.getEncoded();
```

SM2 private key is 32-bytes length without any format.

```
byte[] encodedPrivateKey = privateKey.getEncoded();
```

For more infomation on key pair generation, please refer to the official [KeyPairGenerator] docs.

#### Prepare public key and private key
Generally, in the signing and encrypting operations, the key pairs are already generated. They are not generated on the runtime. It just reads the existing public key and private key to create PublicKey and PrivateKey instances respectively.

```
byte[] encodedPublicKey = <public key>;
byte[] encodedPrivateKey = <private key>;

KeyFactory keyFactory = KeyFactory.getInstance("SM2");

SM2PublicKeySpec publicKeySpec = new SM2PublicKeySpec(encodedPublicKey);
PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

SM2PrivateKeySpec privateKeySpec = new SM2PrivateKeySpec(encodedPrivateKey);
PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
```

#### Signature
Using SM2 signature is the same as using other existing signatures, like ECDSA, though the parameters are different.

Create Signature instance.

```
Signature signature = Signature.getInstance("SM2);
```

Initialize the Signature instance with the private key for signing.

```
signature.initSign(privateKey);
```

Behind the above initialization, it uses the default SM2 ID, exactly `1234567812345678`. It also derives the public key from the private key. According to the associated specification, public key must be used for generating SM2 signature data. This is a significant difference between the international algorithms, like ECDSA, and SM2 signature algorithm. Note that calculating the public key leads to some overhead. That is harmful for the performance. 

If not using the default ID or expect to eliminate the cost on calculating public key, it would provide an AlgorithmParameterSpec, exactly SM2SignatureParameterSpec, instance.

```
byte[] altID = <custom ID>;
ECPublicKey publicKey = <public key>;
SM2SignatureParameterSpec paramSpec = new SM2SignatureParameterSpec(altID, publicKey);
signature.setParameter(paramSpec);
signature.initSign(privateKey);
```

After parameter setup and initialization, it is time to pass the message data in.

```
byte[] message = <the message>;
signature.update(message);
```

Generate signature data.

```
byte[] sign = signature.sign();
```

The SM2 signature data is ASN.1-encoded. The length is between 71 and 73 bytes.

Initialize the Signature instance with public key for verifying.

```
signature.initVerify(publicKey);
```

Pass the message in.

```
byte[] message = <the message>;
signature.update(message);
```

Pass the signature data in.

```
boolean verified = signature.verify(sign);
```

If the verification is successful, the result is true, otherwise false.

It has to know that the private key is used for signing and the public key is used for verifying. For detailed information about Signature APIs, please refer to the official [Signature] docs.

#### Encryption
Because of the performance concern, public key encryption is only used for encrypting short but critical data. The same to SM2 encryption.

Create Cipher instance.

```
Cipher cipher = Cipher.getInstance("SM2");
```

Initialize the Cipher instance with public key and set the mode to encryption.

```
cipher.init(Cipher.ENCRYPT_MODE, publicKey);
```

Pass the message in and generate the ciphertext.

```
byte[] message = <the message>;
byte[] ciphertext = cipher.doFinal(message);
```

Initialize the Cipher instance with private key and set the mode to decryption.

```
cipher.init(Cipher.DECRYPT_MODE, privateKey);
```

Pass the ciphertext in and generate the plaintext.

```
byte[] cleartext = cipher.doFinal(ciphertext);
```

It has to know that public key is used for encryption, however the private key is used for decryption. For detailed information on Cipher APIs, please refer to the official [Cipher] docs.

### SM3
Using SM3 hash algorithm is the same as using other existing hash algorithm, like SHA-256. It only invokes JDK APIs to generate the message digest (hash).

Create MessageDigest instance.

```
MessageDigest md = MessageDigest.getInstance("SM3");
```

It can pass all the message in and generate the digest.

```
byte[] message = <the message>;
byte[] digest = md.digest(message);
```

It also can pass the message segments, and finally generate the digest.

```
byte[] messageSegment1 = <the message segment1>;
byte[] messageSegment2 = <the message segment2>;

// Pass the message segments in
md.update(messageSegment1);
md.update(messageSegment2);

// Finally generate the digest
byte[] digest = md.digest();
```

For detailed information about MessageDigest APIs, please refer to the official [MessageDigest] docs.

### SM3HMac
Using SM3 HMAC algorithm is the same as using other existing MAC algorithm, like HmacSHA256. It only invokes JDK APIs to generate the message authentication code.

Prepare a 16-bytes key.

```
byte[] key = <the key>;
SecretKey secretKey = new SecretKeySpec(key, "SM4");
```

Create Mac instance.

```
Mac hmac = Mac.getInstance("SM3HMac");
```

Initialize the Mac instance with the key.

```
hmac.init(secretKey);
```

Pass all the message in and generate message authentication code. The code is 32-bytes length.

```
byte[] message = <the message>;
byte[] mac = hmac.doFinal(message);
```

It also can pass the message segments, and finally generate the message authentication code.

```
byte[] messageSegment1 = <the message segment1>;
byte[] messageSegment2 = <the message segment2>;

// Pass the message segments in
hmac.update(messageSegment1);
hmac.update(messageSegment2);

// Finally generate the message authentication code
byte[] mac = hmac.doFinal();
```

For the detailed information about Mac APIs, please refer to the official [Mac] docs.

### SM4
Using SM4 cipher algorithm is the same as using other existing block cipher algorithm, like AES. It only invokes JDK APIs to do encryption and decryption. `KonaCrypto` supports four operation modes, including CBC, CTR, ECB and GCM. It also supports PKCS#7 padding specification.

Prepare a 16-bytes key.

```
byte[] key = <the key>;
SecretKey secretKey = new SecretKeySpec(key, "SM4");
```

Create Cipher instance.

```
Cipher cipher = Cipher.getInstance(transformation);
```

The transformation consists of algorithm name, operation mode and padding type. A symbol `/` is used as separator between the parts.

The following transformations are supported:
- SM4/CBC/NoPadding: The operation mode is CBC. No padding is needed. It requires the input must be 16x bytes.
- SM4/CBC/PKCS7Padding: The operation mode is CBC. PKCS#7 padding is used. It doesn't require that the input must be 16x bytes.
- SM4/CTR/NoPadding: The operation mode is CBC. No padding is needed. It doesn't require that the input must be 16x bytes.
- SM4/GCM/NoPadding: The operation mode is GCM. No padding is needed. It doesn't require that the input must be 16x bytes.

Create the algorithm parameters.

```
AlgorithmParameterSpec paramSpec = <the parameter parameter instance>;
```

Different operation modes may need different AlgorithmParameterSpec types,

- CBC and CTR modes use [IvParameterSpec]. This parameter type needs a 16-bytes as Initialization Vector (IV).
- GCM mode uses [GCMParameterSpec]. This parameter type needs a 12-bytes as Initialization Vector (IV) and specifies the tag length to 12-bytes.

Initialize the Cipher instance with the key and parameter type and set the mode to encryption.

```
cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);
```

Pass all the message in and generate the ciphertext.

```
byte[] ciphertext = cipher.doFinal(message);
```

Initialize the Cipher instance with the key and parameter type and set the mode to decryption.

```
cipher.init(Cipher.DECRYPT_MODE, secretKey);
```

Pass all the ciphertext in and generate the plaintext.

```
byte[] cleartext = cipher.doFinal(ciphertext);
```

It also can receive the plaintext/ciphertext segments in, and then generate the ciphertext/plaintext segments.

```
byte[] input1 = <segment1>;
byte[] input2 = <segment2>;

// Pass the plaintext/ciphertext segments in
byte[] output1 = cipher.update(input1);
byte[] output2 = cipher.update(input2);

// Generate the ciphertext/plaintext segments
byte[] outputFinal = hmac.doFinal();
```

For the detailed information about Cipher APIs, please refer to the official [Cipher] docs.


[Java Cryptography Architecture (JCA)]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-2BCFDD85-D533-4E6C-8CE9-29990DEB0190>

[SunJCE]:
<https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-A47B1249-593C-4C38-A0D0-68FA7681E0A7>

[SunEC]:
<https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-091BF58C-82AB-4C9C-850F-1660824D5254>

[JCA reference]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-2BCFDD85-D533-4E6C-8CE9-29990DEB0190>

[Elliptic Curve Cryptography (ECC)]:
<https://en.wikipedia.org/wiki/Elliptic-curve_cryptography>

[KeyPairGenerator]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-7EA29AC2-28B5-405D-BD2F-7055EC9E1EDD>

[MessageDigest]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-FB0090CA-2BCC-4D2C-BD2F-6F0A97197BD7>

[MAC]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-8E014689-EBBB-4DE1-B6E0-24CE59AD8B9A>

[Cipher]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-94225C88-F2F1-44D1-A781-1DD9D5094566>

[Signature]:
<https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-9CF09CE2-9443-4F4E-8095-5CBFC7B697CF>

[IvParameterSpec]:
<https://docs.oracle.com/en/java/javase/11/docs/api/java.base/javax/crypto/spec/IvParameterSpec.html>

[GCMParameterSpec]:
<https://docs.oracle.com/en/java/javase/11/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html>