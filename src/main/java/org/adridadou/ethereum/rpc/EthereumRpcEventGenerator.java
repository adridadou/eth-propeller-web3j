package org.adridadou.ethereum.rpc;

import org.adridadou.ethereum.propeller.event.*;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by davidroon on 30.01.17.
 * This code is released under Apache 2 license
 */
public class EthereumRpcEventGenerator {
    private final List<EthereumEventHandler> ethereumEventHandlers = new ArrayList<>();
    private final EthereumRpc ethereum;

    public EthereumRpcEventGenerator(Web3JFacade web3JFacade, EthereumRpcConfig config, EthereumRpc ethereum) {
        this.ethereum = ethereum;
        if(config.isPollBlocks()) {
            web3JFacade.observeBlocksPolling(config.getPollingFrequence()).subscribe(this::observeBlocks);
        }else {
            web3JFacade.observeBlocks().subscribe(this::observeBlocks);
        }
    }

    private void observeBlocks(EthBlock ethBlock) {
        BlockInfo param = ethereum.toBlockInfo(ethBlock);
        ethereumEventHandlers.forEach(handler -> handler.onBlock(param));

        ethereumEventHandlers
                .forEach(handler -> param.receipts
                        .stream().map(tx -> new TransactionInfo(tx, TransactionStatus.Executed))
                        .forEach(handler::onTransactionExecuted));
    }

    public void addListener(EthereumEventHandler ethereumEventHandler) {
        this.ethereumEventHandlers.add(ethereumEventHandler);
    }
}
