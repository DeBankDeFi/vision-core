package org.vision.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.FileUtil;
import org.vision.core.Constant;
import org.vision.core.Wallet;
import org.vision.core.capsule.AccountCapsule;
import org.vision.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.vision.core.capsule.DelegatedResourceCapsule;
import org.vision.core.capsule.TransactionResultCapsule;
import org.vision.core.config.DefaultConfig;
import org.vision.core.config.Parameter;
import org.vision.core.config.args.Args;
import org.vision.core.db.Manager;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.common.application.VisionApplicationContext;
import org.vision.protos.Protocol.AccountType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.AssetIssueContractOuterClass;
import org.vision.protos.contract.BalanceContract.FreezeBalanceContract;
import org.vision.protos.contract.Common.ResourceCode;

@Slf4j
public class FreezeBalanceActuatorTest {

  private static final String dbPath = "output_freeze_balance_test";
  private static final String OWNER_ADDRESS;
  private static final String RECEIVER_ADDRESS;
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ACCOUNT_INVALID;
  private static final long initBalance = 10_000_000_000L;
  private static Manager dbManager;
  private static VisionApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new VisionApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    RECEIVER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
    OWNER_ACCOUNT_INVALID =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    //    Args.setParam(new String[]{"--output-directory", dbPath},
    //        "config-junit.conf");
    //    dbManager = new Manager();
    //    dbManager.init();
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createAccountCapsule() {
    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            AccountType.Normal,
            initBalance);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    AccountCapsule receiverCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("receiver"),
            ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)),
            AccountType.Normal,
            initBalance);
    dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);
  }

  private Any getContractForPhoton(String ownerAddress, long frozenBalance, long duration) {
    return Any.pack(
        FreezeBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setFrozenBalance(frozenBalance)
            .setFrozenDuration(duration)
            .build());
  }

  private Any getContractForCpu(String ownerAddress, long frozenBalance, long duration) {
    return Any.pack(
        FreezeBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setFrozenBalance(frozenBalance)
            .setFrozenDuration(duration)
            .setResource(ResourceCode.ENTROPY)
            .build());
  }

  private Any getDelegatedContractForPhoton(String ownerAddress, String receiverAddress,
                                            long frozenBalance,
                                            long duration) {
    return Any.pack(
        FreezeBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
            .setFrozenBalance(frozenBalance)
            .setFrozenDuration(duration)
            .build());
  }

  private Any getDelegatedContractForCpu(String ownerAddress, String receiverAddress,
      long frozenBalance,
      long duration) {
    return Any.pack(
        FreezeBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
            .setFrozenBalance(frozenBalance)
            .setFrozenDuration(duration)
            .setResource(ResourceCode.ENTROPY)
            .build());
  }

  @Test
  public void testFreezeBalanceForPhoton() {
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(owner.getFrozenBalance(), frozenBalance);
      Assert.assertEquals(frozenBalance, owner.getVisionPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testFreezeBalanceForEntropy() {
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForCpu(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getFrozenBalance());
      Assert.assertEquals(frozenBalance, owner.getEntropyFrozenBalance());
      Assert.assertEquals(frozenBalance, owner.getVisionPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testFreezeDelegatedBalanceForPhotonWithContractAddress() {
    AccountCapsule receiverCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("receiver"),
            ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)),
            AccountType.Contract,
            initBalance);
    dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);

    dbManager.getDynamicPropertiesStore().saveAllowVvmConstantinople(1);

    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForPhoton(OWNER_ADDRESS, RECEIVER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(), "Do not allow delegate resources to contract addresses");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testFreezeDelegatedBalanceForPhoton() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForPhoton(OWNER_ADDRESS, RECEIVER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long totalPhotonWeightBefore = dbManager.getDynamicPropertiesStore().getTotalPhotonWeight();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getFrozenBalance());
      Assert.assertEquals(frozenBalance, owner.getDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(frozenBalance, owner.getVisionPower());

      AccountCapsule receiver =
          dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(frozenBalance, receiver.getAcquiredDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedFrozenBalanceForEntropy());
      Assert.assertEquals(0L, receiver.getVisionPower());

      DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
          .get(DelegatedResourceCapsule
              .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
                  ByteArray.fromHexString(RECEIVER_ADDRESS)));

      Assert.assertEquals(frozenBalance, delegatedResourceCapsule.getFrozenBalanceForPhoton());
      long totalPhotonWeightAfter = dbManager.getDynamicPropertiesStore().getTotalPhotonWeight();
      Assert.assertEquals(totalPhotonWeightBefore + frozenBalance / 1000_000L, totalPhotonWeightAfter);

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(1, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS))));

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert
          .assertEquals(1,
              delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS))));


    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testFreezeDelegatedBalanceForCpuSameNameTokenActive() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long totalEntropyWeightBefore = dbManager.getDynamicPropertiesStore().getTotalEntropyWeight();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getFrozenBalance());
      Assert.assertEquals(0L, owner.getDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(frozenBalance, owner.getDelegatedFrozenBalanceForEntropy());
      Assert.assertEquals(frozenBalance, owner.getVisionPower());

      AccountCapsule receiver =
          dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(frozenBalance, receiver.getAcquiredDelegatedFrozenBalanceForEntropy());
      Assert.assertEquals(0L, receiver.getVisionPower());

      DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
          .get(DelegatedResourceCapsule
              .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
                  ByteArray.fromHexString(RECEIVER_ADDRESS)));

      Assert.assertEquals(0L, delegatedResourceCapsule.getFrozenBalanceForPhoton());
      Assert.assertEquals(frozenBalance, delegatedResourceCapsule.getFrozenBalanceForEntropy());

      long totalEntropyWeightAfter = dbManager.getDynamicPropertiesStore().getTotalEntropyWeight();
      Assert.assertEquals(totalEntropyWeightBefore + frozenBalance / 1000_000L,
          totalEntropyWeightAfter);

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(1, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS))));

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert
          .assertEquals(1,
              delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS))));

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testFreezeDelegatedBalanceForCpuSameNameTokenClose() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(0);
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long totalEntropyWeightBefore = dbManager.getDynamicPropertiesStore().getTotalEntropyWeight();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(owner.getBalance(), initBalance - frozenBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getFrozenBalance());
      Assert.assertEquals(0L, owner.getDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(0L, owner.getDelegatedFrozenBalanceForEntropy());
      Assert.assertEquals(0L, owner.getDelegatedFrozenBalanceForEntropy());

      AccountCapsule receiver =
          dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedFrozenBalanceForPhoton());
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedFrozenBalanceForEntropy());
      Assert.assertEquals(0L, receiver.getVisionPower());

      long totalEntropyWeightAfter = dbManager.getDynamicPropertiesStore().getTotalEntropyWeight();
      Assert.assertEquals(totalEntropyWeightBefore + frozenBalance / 1000_000L,
          totalEntropyWeightAfter);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void freezeLessThanZero() {
    long frozenBalance = -1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("frozenBalance must be positive", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void freezeMoreThanBalance() {
    long frozenBalance = 11_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("frozenBalance must be less than accountBalance", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void invalidOwnerAddress() {
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS_INVALID, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);

      Assert.assertEquals("Invalid address", e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }

  }

  @Test
  public void invalidOwnerAccount() {
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ACCOUNT_INVALID, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ACCOUNT_INVALID + "] not exists",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void durationLessThanMin() {
    long frozenBalance = 1_000_000_000L;
    long duration = 2;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      long minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
      long maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("frozenDuration must be less than " + maxFrozenTime + " days "
          + "and more than " + minFrozenTime + " days", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void durationMoreThanMax() {
    long frozenBalance = 1_000_000_000L;
    long duration = 4;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      long minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
      long maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("frozenDuration must be less than " + maxFrozenTime + " days "
          + "and more than " + minFrozenTime + " days", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void lessThan1VsTest() {
    long frozenBalance = 1;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("frozenBalance must be more than 1VS", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void frozenNumTest() {
    AccountCapsule account = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    account.setFrozen(1_000L, 1_000_000_000L);
    account.setFrozen(1_000_000L, 1_000_000_000L);
    dbManager.getAccountStore().put(account.getAddress().toByteArray(), account);

    long frozenBalance = 20_000_000L;
    long duration = 3L;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("frozenCount must be 0 or 1", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  //@Test
  public void moreThanFrozenNumber() {
    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      long maxFrozenNumber = Parameter.ChainConstant.MAX_FROZEN_NUMBER;
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("max frozen number is: " + maxFrozenNumber, e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void commonErrorCheck() {
    FreezeBalanceActuator actuator = new FreezeBalanceActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error,expected type [FreezeBalanceContract],real type[");
    actuatorTest.invalidContractType();

    long frozenBalance = 1_000_000_000L;
    long duration = 3;
    actuatorTest.setContract(getContractForPhoton(OWNER_ADDRESS, frozenBalance, duration));
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }


}
