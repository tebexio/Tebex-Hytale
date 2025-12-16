package io.tebex.sdk.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.annotation.Nullable;
import java.net.URI;

@Data @AllArgsConstructor
public class Request {
    private Verb method;
    private URI url;
    @Nullable private Object body; //TODO
}
