package stest.vision.wallet.newaddinterface2;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.AccountPhotonMessage;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class CreateAccount2Test {

  private static final long now = System.currentTimeMillis();
  private static final long totalSupply = now;
  private static final long sendAmount = 10000000000L;
  private static final long FREEPHOTONLIMIT = 5000L;
  private static final long BASELINE = 4800L;
  private static String name = "AssetIssue012_" + Long.toString(now);
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  //owner account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] account007Address = ecKey1.getAddress();
  String account007Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  //Wait to be create account
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] newAccountAddress = ecKey2.getAddress();
  String newAccountKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    logger.info(account007Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    Assert.assertTrue(PublicMethed.sendcoin(account007Address, 10000000,
        fromAddress, testKey002, blockingStubFull));
  }

  @Test(enabled = true)
  public void testCreateAccount2() {
    Account accountInfo = PublicMethed.queryAccount(account007Key, blockingStubFull);
    final Long beforeBalance = accountInfo.getBalance();
    AccountPhotonMessage accountPhotonInfo = PublicMethed.getAccountPhoton(account007Address,
        blockingStubFull);
    final Long beforeFreePhoton = accountPhotonInfo.getFreePhotonUsed();
    GrpcAPI.Return ret1 = PublicMethed.createAccount2(account007Address, newAccountAddress,
        account007Key, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    accountInfo = PublicMethed.queryAccount(account007Key, blockingStubFull);
    Long afterBalance = accountInfo.getBalance();
    accountPhotonInfo = PublicMethed.getAccountPhoton(account007Address,
        blockingStubFull);
    Long afterFreePhoton = accountPhotonInfo.getFreePhotonUsed();
    logger.info(Long.toString(beforeBalance));
    logger.info(Long.toString(afterBalance));
    //When creator has no photon, he can't use the free net.
    Assert.assertTrue(afterFreePhoton == beforeFreePhoton);
    //When the creator has no photon, create a new account should spend 0.1VS.
    Assert.assertTrue(beforeBalance - afterBalance == 100000);
  }

  @Test(enabled = true)
  public void testExceptionCreateAccount2() {
    //Try to create an exist account
    GrpcAPI.Return ret1 = PublicMethed
        .createAccount2(account007Address, account007Address, account007Key,
            blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : Account has existed");
    //Try to create an invalid account
    byte[] wrongAddress = "wrongAddress".getBytes();
    ret1 = PublicMethed.createAccount2(account007Address, wrongAddress, account007Key,
        blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : Invalid account address");
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


