package com.advantech.uvc.Agora;

public interface PackableEx extends Packable {
    void unmarshal(ByteBuf in);
}
