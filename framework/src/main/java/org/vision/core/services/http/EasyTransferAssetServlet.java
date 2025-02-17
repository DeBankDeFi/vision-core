package org.vision.core.services.http;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.EasyTransferAssetMessage;
import org.vision.api.GrpcAPI.EasyTransferResponse;
import org.vision.api.GrpcAPI.Return.response_code;
import org.vision.common.crypto.SignInterface;
import org.vision.common.crypto.SignUtils;
import org.vision.core.Wallet;
import org.vision.core.capsule.TransactionCapsule;
import org.vision.core.config.args.Args;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.services.http.JsonFormat.ParseException;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;


@Component
@Slf4j
public class EasyTransferAssetServlet extends RateLimiterServlet {

  private static final String S_IOEXCEPTION = "IOException: {}";

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
    EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
    boolean visible = false;
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      visible = Util.getVisiblePost(input);
      EasyTransferAssetMessage.Builder build = EasyTransferAssetMessage.newBuilder();
      JsonFormat.merge(input, build, visible);
      byte[] privateKey = wallet.pass2Key(build.getPassPhrase().toByteArray());
      SignInterface ecKey = SignUtils.fromPrivate(privateKey, Args.getInstance()
          .isECKeyCryptoEngine());
      byte[] owner = ecKey.getAddress();
      TransferAssetContract.Builder builder = TransferAssetContract.newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(owner));
      builder.setToAddress(build.getToAddress());
      builder.setAssetName(ByteString.copyFrom(build.getAssetId().getBytes()));
      builder.setAmount(build.getAmount());

      TransactionCapsule transactionCapsule;
      transactionCapsule = wallet
          .createTransactionCapsule(builder.build(), ContractType.TransferAssetContract);
      transactionCapsule.sign(privateKey);
      GrpcAPI.Return result = wallet.broadcastTransaction(transactionCapsule.getInstance());
      responseBuild.setTransaction(transactionCapsule.getInstance());
      responseBuild.setResult(result);
      response.getWriter().println(Util.printEasyTransferResponse(responseBuild.build(), visible));
    } catch (ParseException e) {
      logger.debug("ParseException: {}", e.getMessage());
      returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
      responseBuild.setResult(returnBuilder.build());
      try {
        response.getWriter().println(JsonFormat.printToString(responseBuild.build(), visible));
      } catch (IOException ioe) {
        logger.debug(S_IOEXCEPTION, ioe.getMessage());
      }
      return;
    } catch (IOException e) {
      logger.debug(S_IOEXCEPTION, e.getMessage());
      returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
      responseBuild.setResult(returnBuilder.build());
      try {
        response.getWriter().println(JsonFormat.printToString(responseBuild.build(), visible));
      } catch (IOException ioe) {
        logger.debug(S_IOEXCEPTION, ioe.getMessage());
      }
      return;
    } catch (ContractValidateException e) {
      returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
      responseBuild.setResult(returnBuilder.build());
      try {
        response.getWriter().println(JsonFormat.printToString(responseBuild.build(), visible));
      } catch (IOException ioe) {
        logger.debug(S_IOEXCEPTION, ioe.getMessage());
      }
      return;
    }
  }
}
