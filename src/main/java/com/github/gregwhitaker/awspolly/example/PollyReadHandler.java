/*
 * Copyright 2017 Greg Whitaker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gregwhitaker.awspolly.example;

import com.amazonaws.services.polly.AmazonPollyAsyncClient;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.google.inject.Inject;
import io.netty.buffer.Unpooled;
import org.reactivestreams.Subscription;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.io.IOException;

public class PollyReadHandler implements Handler {

    @Inject
    private AmazonPollyAsyncClient polly;

    @Override
    public void handle(Context ctx) throws Exception {
        String voiceId = ctx.getRequest().getQueryParams().get("voiceId");
        String text = ctx.getRequest().getQueryParams().get("text");
        String outputFormat = ctx.getRequest().getQueryParams().get("outputFormat");

        SynthesizeSpeechRequest ssRequest = new SynthesizeSpeechRequest();
        ssRequest.setVoiceId(voiceId);
        ssRequest.setOutputFormat(outputFormat);
        ssRequest.setText(text);

        SynthesizeSpeechResult result = polly.synthesizeSpeech(ssRequest);

        System.out.println("Handling request..." + result.getContentType());
        
        ctx.getResponse().contentType(result.getContentType());
        ctx.getResponse().sendStream(s -> s.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                try {
                    byte[] data      = new byte[4096];
                    int bytesRead = result.getAudioStream().read(data);
                    System.out.println("Chunk from aws " + bytesRead);
                    while(bytesRead != -1) {
                        s.onNext(Unpooled.wrappedBuffer(data));
                        bytesRead = result.getAudioStream().read(data);
                    }
                    //System.out.println("Done stream");
                } catch (IOException e) {
                    e.printStackTrace();
                    ctx.getResponse().status(500);
                    ctx.getResponse().send();
                } catch (Throwable e) {
                    e.printStackTrace();
                    ctx.getResponse().status(500);
                    ctx.getResponse().send();
                } finally {
                    s.onComplete();
                }
                System.out.println("Done request");
            }

            @Override
            public void cancel() {
                System.out.println("CANCELLED");
            }
        }));
    }
}
