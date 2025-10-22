#ifndef KIKCODE_ENCODING_H_

#include <iostream>

// data constants
#define KIK_CODE_BYTE_COUNT          (312/8)
#define KIK_CODE_ALL_BYTE_COUNT      (280/8)
#define KIK_CODE_DATA_BYTE_COUNT     (176/8)
#define KIK_CODE_ECC_BYTE_COUNT      (104/8)
#define KIK_CODE_PAYLOAD_BYTE_COUNT  (160/8)

class KikCode {
public:
    enum class Colour {
        Default = 0,
        TestFive = 5,
        TestEleven = 11,
        TestFourteen = 14,

    };

    enum class Type {
        Username = 1,
        Remote = 2,
        Group = 3
    };

protected:
    Type type_;
    Colour colour_;
    uint8_t extra_;

    size_t writeByte(unsigned char *out_data, size_t offset, unsigned char value);

    static size_t writeShort(unsigned char *out_data, size_t offset, unsigned short value);

    size_t writeInt(unsigned char *out_data, size_t offset, unsigned int value);

    size_t writeLong(unsigned char *out_data, size_t offset, unsigned long long value);

    unsigned char readByte(unsigned char *data, size_t offset);

    unsigned short readShort(unsigned char *data, size_t offset);

    unsigned int readInt(unsigned char *data, size_t offset);

    unsigned long long readLong(unsigned char *data, size_t offset);

    virtual void decode(uint8_t *data_section);

public:
    KikCode(Type type, Colour colour);

    virtual ~KikCode();

    Colour colour() const;
    Type type() const;
    uint8_t extra() const;

    static KikCode *parse(const uint8_t *data);

    virtual void encode(uint8_t *out_data);
};

class UsernameKikCode : public KikCode {
private:
    friend class KikCode;

    std::string username_;
    uint16_t nonce_;

    explicit UsernameKikCode(KikCode::Colour colour);

protected:
    void decode(uint8_t *data_section) override;

public:
    UsernameKikCode(std::string username, uint16_t nonce, KikCode::Colour colour);

    void encode(uint8_t *out_data) override;

    std::string username() const;
    uint16_t nonce() const;
};

class RemoteKikCode : public KikCode {
private:
    friend class KikCode;

    std::string payload_;

    explicit RemoteKikCode(KikCode::Colour colour);

protected:
    void decode(uint8_t *data_section) override;

public:
    RemoteKikCode(std::string payload, KikCode::Colour colour);

    void encode(uint8_t *out_data) override;

    std::string payload() const;
};

class GroupKikCode : public KikCode {
private:
    friend class KikCode;

    std::string invite_code_;

    explicit GroupKikCode(KikCode::Colour colour);

protected:
    void decode(uint8_t *data_section) override;

public:
    GroupKikCode(const std::string &payload, KikCode::Colour colour);

    void encode(uint8_t *out_data) override;

    std::string inviteCode() const;
};

#endif // KIKCODE_ENCODING_H_
