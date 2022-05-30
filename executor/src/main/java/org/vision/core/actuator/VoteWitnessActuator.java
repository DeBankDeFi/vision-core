package org.vision.core.actuator;

import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.vision.common.parameter.CommonParameter;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.DecodeUtil;
import org.vision.common.utils.StringUtil;
import org.vision.core.capsule.*;
import org.vision.core.config.Parameter;
import org.vision.core.exception.ContractExeException;
import org.vision.core.exception.ContractValidateException;
import org.vision.core.service.MortgageService;
import org.vision.core.store.*;
import org.vision.protos.Protocol.Transaction.Contract.ContractType;
import org.vision.protos.Protocol.Transaction.Result.code;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract.Vote;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.vision.core.actuator.ActuatorConstant.*;
import static org.vision.core.config.Parameter.ChainConstant.*;
import static org.vision.core.config.Parameter.ChainConstant.FROZEN_PERIOD;

@Slf4j(topic = "actuator")
public class VoteWitnessActuator extends AbstractActuator {

  public VoteWitnessActuator() {
    super(ContractType.VoteWitnessContract, VoteWitnessContract.class);
  }

  @Override
  public boolean execute(Object object) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) object;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    try {
      VoteWitnessContract voteContract = any.unpack(VoteWitnessContract.class);
      countVoteAccount(voteContract);
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    WitnessStore witnessStore = chainBaseManager.getWitnessStore();
    if (!this.any.is(VoteWitnessContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [VoteWitnessContract], real type[" + any
              .getClass() + "]");
    }
    final VoteWitnessContract contract;
    try {
      contract = this.any.unpack(VoteWitnessContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    if (!DecodeUtil.addressValid(contract.getOwnerAddress().toByteArray())) {
      throw new ContractValidateException("Invalid address");
    }
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (contract.getVotesCount() == 0) {
      throw new ContractValidateException(
          "VoteNumber must more than 0");
    }
    int maxVoteNumber = MAX_VOTE_NUMBER;
    if (contract.getVotesCount() > maxVoteNumber) {
      throw new ContractValidateException(
          "VoteNumber more than maxVoteNumber " + maxVoteNumber);
    }
    try {
      Iterator<Vote> iterator = contract.getVotesList().iterator();
      Long sum = 0L;
      while (iterator.hasNext()) {
        Vote vote = iterator.next();
        byte[] witnessCandidate = vote.getVoteAddress().toByteArray();
        if (!DecodeUtil.addressValid(witnessCandidate)) {
          throw new ContractValidateException("Invalid vote address!");
        }
        long voteCount = vote.getVoteCount();
        if (voteCount <= 0) {
          throw new ContractValidateException("vote count must be greater than 0");
        }
        String readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());
        if (!accountStore.has(witnessCandidate)) {
          throw new ContractValidateException(
              ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }
        if (!witnessStore.has(witnessCandidate)) {
          throw new ContractValidateException(
              WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }
        sum = LongMath.checkedAdd(sum, vote.getVoteCount());
      }

      AccountCapsule accountCapsule = accountStore.get(ownerAddress);
      if (accountCapsule == null) {
        throw new ContractValidateException(
            ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      long visionPower = accountCapsule.getVisionPower();

      sum = LongMath
          .checkedMultiply(sum, VS_PRECISION); //vs -> drop. The vote count is based on VS
      if (sum > visionPower) {
        throw new ContractValidateException(
            "The total number of votes[" + sum + "] is greater than the visionPower[" + visionPower
                + "]");
      }
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  private void countVoteAccount(VoteWitnessContract voteContract) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    VotesStore votesStore = chainBaseManager.getVotesStore();
    MortgageService mortgageService = chainBaseManager.getMortgageService();
    byte[] ownerAddress = voteContract.getOwnerAddress().toByteArray();

    VotesCapsule votesCapsule;

    //
    mortgageService.withdrawReward(ownerAddress, false);

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);

    if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(voteContract.getOwnerAddress(),
          accountCapsule.getVotesList());
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }

    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    voteContract.getVotesList().forEach(vote -> {
      logger.debug("countVoteAccount, address[{}]",
          ByteArray.toHexString(vote.getVoteAddress().toByteArray()));
      // get freeze and compute a new votes
      DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
      long interval1 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel1(), VS_PRECISION);
      long interval2 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel2(), VS_PRECISION);
      long interval3 = LongMath.checkedMultiply(dynamicStore.getVoteFreezeStageLevel3(), VS_PRECISION);
      long visionPower = accountCapsule.getVisionPower();
      long voteCount = vote.getVoteCount();
      if (visionPower >= interval3) {
        voteCount = (long) (voteCount * ((float) dynamicStore.getVoteFreezePercentLevel3() / Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
      } else if (visionPower >= interval2) {
        voteCount = (long) (voteCount * ((float) dynamicStore.getVoteFreezePercentLevel2() /Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
      } else if (visionPower >= interval1) {
        voteCount = (long) (voteCount * ((float) dynamicStore.getVoteFreezePercentLevel1() /Parameter.ChainConstant.VOTE_PERCENT_PRECISION));
      }
      if (dynamicStore.getAllowVPFreezeStageWeight() == 1L) {
        voteCount = (long) (voteCount * (accountCapsule.getFrozenStageWeightMerge() * 1.0 / 100L));

        AccountFrozenStageResourceStore accountFrozenStageResourceStore = chainBaseManager.getAccountFrozenStageResourceStore();
        Map<Long, List<Long>> stageWeights = dynamicStore.getVPFreezeStageWeights();
        long now = dynamicStore.getLatestBlockHeaderTimestamp();
        long consider = dynamicStore.getRefreezeConsiderationPeriod() * FROZEN_PERIOD;
        for (Map.Entry<Long, List<Long>> entry : stageWeights.entrySet()) {
          if (entry.getKey() == 1L) {
            continue;
          }
          byte[] key = AccountFrozenStageResourceCapsule.createDbKey(ownerAddress, entry.getKey());
          AccountFrozenStageResourceCapsule capsule = accountFrozenStageResourceStore.get(key);
          if (capsule == null) {
            continue;
          }

          if (capsule.getInstance().getFrozenBalanceForPhoton() > 0
              && capsule.getInstance().getExpireTimeForPhoton() < now - consider) {
            long cycle = (now - capsule.getInstance().getExpireTimeForPhoton())
                / entry.getValue().get(0) / FROZEN_PERIOD;
            long tmp = capsule.getInstance().getExpireTimeForPhoton() +
                (cycle + 1) * entry.getValue().get(0) * FROZEN_PERIOD;
            capsule.setFrozenBalanceForPhoton(capsule.getInstance().getFrozenBalanceForPhoton(), tmp);
            accountFrozenStageResourceStore.put(key, capsule);
            accountCapsule.setFrozenForPhoton(accountCapsule.getFrozenBalance(),
                Math.max(accountCapsule.getFrozenExpireTime(), tmp));
          }
          if (capsule.getInstance().getFrozenBalanceForEntropy() > 0
              && capsule.getInstance().getExpireTimeForEntropy() < now - consider) {
            long cycle = (now - capsule.getInstance().getExpireTimeForEntropy())
                / entry.getValue().get(0) / FROZEN_PERIOD;
            long tmp = capsule.getInstance().getExpireTimeForEntropy() +
                (cycle + 1) * entry.getValue().get(0) * FROZEN_PERIOD;
            capsule.setFrozenBalanceForEntropy(capsule.getInstance().getFrozenBalanceForEntropy(), tmp);
            accountFrozenStageResourceStore.put(key, capsule);
            accountCapsule.setFrozenForEntropy(accountCapsule.getEntropyFrozenBalance(),
                Math.max(accountCapsule.getEntropyFrozenExpireTime(), tmp));
          }
        }

        long spreadConsider = dynamicStore.getSpreadRefreezeConsiderationPeriod() * FROZEN_PERIOD;
        long spreadBalance = accountCapsule.getAccountResource().getFrozenBalanceForSpread().getFrozenBalance();
        long spreadExpireTime = accountCapsule.getAccountResource().getFrozenBalanceForSpread().getExpireTime();
        if (spreadBalance > 0 && spreadExpireTime < now - spreadConsider) {
          long cycle = (now - spreadExpireTime) / FROZEN_PERIOD / dynamicStore.getSpreadFreezePeriodLimit();
          spreadExpireTime += (cycle + 1) * dynamicStore.getSpreadFreezePeriodLimit() * FROZEN_PERIOD;
          accountCapsule.setFrozenForSpread(spreadBalance, spreadExpireTime);

          SpreadRelationShipStore spreadRelationShipStore = chainBaseManager.getSpreadRelationShipStore();
          SpreadRelationShipCapsule spreadRelationShipCapsule = spreadRelationShipStore.get(ownerAddress);
          if (spreadRelationShipCapsule != null) {
            spreadRelationShipCapsule.setFrozenBalanceForSpread(spreadBalance, spreadExpireTime, dynamicStore.getCurrentCycleNumber());
            if (dynamicStore.getLatestBlockHeaderNumber() >= CommonParameter.PARAMETER.spreadMintUnfreezeClearRelationShipEffectBlockNum){
              spreadRelationShipStore.put(ownerAddress, spreadRelationShipCapsule);
            }
          }
        } //end spread
      }
      votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);
      accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount(), voteCount);

    });

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    votesStore.put(ownerAddress, votesCapsule);
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(VoteWitnessContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
