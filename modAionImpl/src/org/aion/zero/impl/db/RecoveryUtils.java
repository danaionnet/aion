package org.aion.zero.impl.db;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.interfaces.block.Block;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.config.CfgDb;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.AionHubUtils;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Methods used by CLI calls for debugging the local blockchain data.
 *
 * @author Alexandra Roatis
 * @implNote This class started off with helper functions for data recovery at runtime. It evolved
 *     into a diverse set of CLI calls for displaying or manipulating the data. It would benefit
 *     from refactoring to separate the different use cases.
 */
public class RecoveryUtils {

    public enum Status {
        SUCCESS,
        FAILURE,
        ILLEGAL_ARGUMENT
    }

    /** Used by the CLI call. */
    public static Status revertTo(long nbBlock) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "INFO");
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionBlockchainImpl blockchain = AionBlockchainImpl.inst();

        Status status = revertTo(blockchain, nbBlock);

        blockchain.getRepository().close();

        // ok if we managed to get down to the expected block
        return status;
    }

    /** Used by the CLI call. */
    public static void pruneAndCorrect() {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        cfg.getDb().setHeapCacheEnabled(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "INFO");
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionBlockchainImpl blockchain = AionBlockchainImpl.inst();

        IBlockStoreBase store = blockchain.getBlockStore();

        Block bestBlock = store.getBestBlock();
        if (bestBlock == null) {
            System.out.println("Empty database. Nothing to do.");
            return;
        }

        // revert to block number and flush changes
        store.pruneAndCorrect();
        store.flush();

        blockchain.getRepository().close();
    }

    /** Used by the CLI call. */
    public static void dbCompact() {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        cfg.getDb().setHeapCacheEnabled(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "INFO");
        cfgLog.put("GEN", "INFO");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        // compact database after the changes were applied
        repository.compact();
        repository.close();
    }

    /** Used by the CLI call. */
    public static void dumpBlocks(long count) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        cfg.getDb().setHeapCacheEnabled(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");
        cfgLog.put("GEN", "ERROR");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        AionBlockStore store = repository.getBlockStore();
        try {
            String file = store.dumpPastBlocks(count, cfg.getBasePath());
            if (file == null) {
                System.out.println("The database is empty. Cannot print block information.");
            } else {
                System.out.println("Block information stored in " + file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        repository.close();
    }

    /** Used by internal world state recovery method. */
    public static Status revertTo(IAionBlockchain blockchain, long nbBlock) {
        IBlockStoreBase store = blockchain.getBlockStore();

        Block bestBlock = store.getBestBlock();
        if (bestBlock == null) {
            System.out.println("Empty database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }

        long nbBestBlock = bestBlock.getNumber();

        System.out.println(
                "Attempting to revert best block from " + nbBestBlock + " to " + nbBlock + " ...");

        // exit with warning if the given block is larger or negative
        if (nbBlock < 0) {
            System.out.println(
                    "Negative values <"
                            + nbBlock
                            + "> cannot be interpreted as block numbers. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBestBlock == 0) {
            System.out.println("Only genesis block in database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBlock == nbBestBlock) {
            System.out.println(
                    "The block "
                            + nbBlock
                            + " is the current best block stored in the database. Nothing to do.");
            return Status.ILLEGAL_ARGUMENT;
        }
        if (nbBlock > nbBestBlock) {
            System.out.println(
                    "The block #"
                            + nbBlock
                            + " is greater than the current best block #"
                            + nbBestBlock
                            + " stored in the database. "
                            + "Cannot move to that block without synchronizing with peers. Start Aion instance to sync.");
            return Status.ILLEGAL_ARGUMENT;
        }

        // revert to block number and flush changes
        store.revert(nbBlock);
        store.flush();

        nbBestBlock = store.getBestBlock().getNumber();

        // ok if we managed to get down to the expected block
        return (nbBestBlock == nbBlock) ? Status.SUCCESS : Status.FAILURE;
    }

    public static void printStateTrieSize(long blockNumber) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();
        AionBlockStore store = repository.getBlockStore();

        long topBlock = store.getBestBlock().getNumber();
        if (topBlock < 0) {
            System.out.println("The database is empty. Cannot print block information.");
            return;
        }

        long targetBlock = topBlock - blockNumber + 1;
        if (targetBlock < 0) {
            targetBlock = 0;
        }

        AionBlock block;
        byte[] stateRoot;

        while (targetBlock <= topBlock) {
            block = store.getChainBlockByNumber(targetBlock);
            if (block != null) {
                stateRoot = block.getStateRoot();
                try {
                    System.out.println(
                            "Block hash: "
                                    + block.getShortHash()
                                    + ", number: "
                                    + block.getNumber()
                                    + ", tx count: "
                                    + block.getTransactionsList().size()
                                    + ", state trie kv count = "
                                    + repository.getWorldState().getTrieSize(stateRoot));
                } catch (RuntimeException e) {
                    System.out.println(
                            "Block hash: "
                                    + block.getShortHash()
                                    + ", number: "
                                    + block.getNumber()
                                    + ", tx count: "
                                    + block.getTransactionsList().size()
                                    + ", state trie kv count threw exception: "
                                    + e.getMessage());
                }
            } else {
                long count = store.getBlocksByNumber(targetBlock).size();
                System.out.println(
                        "Null block found at level "
                                + targetBlock
                                + ". There "
                                + (count == 1 ? "is 1 block" : "are " + count + " blocks")
                                + " at this level. No main chain block found.");
            }
            targetBlock++;
        }

        repository.close();
    }

    public static void printStateTrieDump(long blockNumber) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");

        AionLoggerFactory.init(cfgLog);

        // get the current blockchain
        AionRepositoryImpl repository = AionRepositoryImpl.inst();

        AionBlockStore store = repository.getBlockStore();

        AionBlock block;

        if (blockNumber == -1L) {
            block = store.getBestBlock();
            if (block == null) {
                System.out.println("The requested block does not exist in the database.");
                return;
            }
            blockNumber = block.getNumber();
        } else {
            block = store.getChainBlockByNumber(blockNumber);
            if (block == null) {
                System.out.println("The requested block does not exist in the database.");
                return;
            }
        }

        byte[] stateRoot = block.getStateRoot();
        System.out.println(
                "\nBlock hash: "
                        + block.getShortHash()
                        + ", number: "
                        + blockNumber
                        + ", tx count: "
                        + block.getTransactionsList().size()
                        + "\n\n"
                        + repository.getWorldState().getTrieDump(stateRoot));

        repository.close();
    }

    public static void pruneOrRecoverState(String pruning_type) {
        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.fromXML();
        cfg.getConsensus().setMining(false);

        // setting pruning to the version requested
        CfgDb.PruneOption option = CfgDb.PruneOption.fromValue(pruning_type);
        cfg.getDb().setPrune(option.toString());

        System.out.println("Reorganizing the state storage to " + option + " mode ...");

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("DB", "ERROR");
        cfgLog.put("CONS", "ERROR");

        AionLoggerFactory.init(cfgLog);

        AionBlockchainImpl chain = AionBlockchainImpl.inst();
        AionRepositoryImpl repo = chain.getRepository();
        AionBlockStore store = repo.getBlockStore();

        // dropping old state database
        System.out.println("Deleting old data ...");
        repo.getStateDatabase().drop();
        if (pruning_type.equals("spread")) {
            repo.getStateArchiveDatabase().drop();
        }

        // recover genesis
        System.out.println("Rebuilding genesis block ...");
        AionGenesis genesis = cfg.getGenesis();
        AionHubUtils.buildGenesis(genesis, repo);

        // recover all blocks
        AionBlock block = store.getBestBlock();
        System.out.println(
                "Rebuilding the main chain "
                        + block.getNumber()
                        + " blocks (may take a while) ...");

        long topBlockNumber = block.getNumber();
        long blockNumber = 1000;

        // recover in increments of 1k blocks
        while (blockNumber < topBlockNumber) {
            block = store.getChainBlockByNumber(blockNumber);
            chain.recoverWorldState(repo, block);
            System.out.println("Finished with blocks up to " + blockNumber + ".");
            blockNumber += 1000;
        }

        block = store.getBestBlock();
        chain.recoverWorldState(repo, block);

        repo.close();
        System.out.println("Reorganizing the state storage COMPLETE.");
    }

    /**
     * Alternative to performing a full sync when the database already contains the <b>blocks</b>
     * and <b>index</b> databases. It will rebuild the entire blockchain structure other than these
     * two databases verifying consensus properties. It only redoes imports of the main chain
     * blocks, i.e. does not perform the checks for side chains.
     *
     * <p>The minimum start height is 0, i.e. the genesis block. Specifying a height can be useful
     * in performing the operation is sessions.
     *
     * @param startHeight the height from which to start importing the blocks
     * @implNote The assumption is that the stored blocks are correct, but the code may interpret
     *     them differently.
     */
    public static void redoMainChainImport(long startHeight) {
        if (startHeight < 0) {
            System.out.println("Negative values are not valid as starting height. Nothing to do.");
            return;
        }

        // ensure mining is disabled
        CfgAion cfg = CfgAion.inst();
        cfg.dbFromXML();
        cfg.getConsensus().setMining(false);
        cfg.getDb().setHeapCacheEnabled(true);

        System.out.println("\nImporting stored blocks INITIATED...\n");

        Map<String, String> cfgLog = new HashMap<>();
        cfgLog.put("GEN", "INFO");
        cfgLog.put("DB", "WARN");
        cfgLog.put("CONS", "ERROR");

        AionLoggerFactory.init(cfgLog);

        AionBlockchainImpl chain = AionBlockchainImpl.inst();
        AionRepositoryImpl repo = chain.getRepository();
        AionBlockStore store = repo.getBlockStore();

        // determine the parameters of the rebuild
        AionBlock block = store.getBestBlock();
        AionBlock startBlock;
        long currentBlock;
        if (block != null && startHeight <= block.getNumber()) {
            System.out.println(
                    "\nImporting the main chain from block #"
                            + startHeight
                            + " to block #"
                            + block.getNumber()
                            + ". This may take a while.\n"
                            + "The time estimates are optimistic based on current progress.\n"
                            + "It is expected that later blocks take a longer time to import due to the increasing size of the database.\n");

            if (startHeight == 0L) {
                // dropping databases that can be inferred when starting from genesis
                List<String> keep = List.of("block", "index");
                repo.dropDatabasesExcept(keep);

                // recover genesis
                AionGenesis genesis = cfg.getGenesis();
                AionHubUtils.buildGenesis(genesis, repo);
                System.out.println("\nFinished rebuilding genesis block.");
                startBlock = genesis;
                currentBlock = 1L;
            } else {
                startBlock = store.getChainBlockByNumber(startHeight - 1);
                currentBlock = startHeight;
            }

            boolean fail = false;

            if (startBlock == null) {
                System.out.println(
                        "The main chain block at level "
                                + currentBlock
                                + " is missing from the database. Cannot continue importing stored blocks.");
                fail = true;
            } else {
                chain.setBestBlock(startBlock);

                long topBlockNumber = block.getNumber();
                long stepSize = 10_000L;

                Pair<ImportResult, AionBlockSummary> result;
                final int THOUSAND_MS = 1000;

                long start = System.currentTimeMillis();

                // import in increments of 10k blocks
                while (currentBlock <= topBlockNumber) {
                    block = store.getChainBlockByNumber(currentBlock);
                    if (block == null) {
                        System.out.println(
                                "The main chain block at level "
                                        + currentBlock
                                        + " is missing from the database. Cannot continue importing stored blocks.");
                        fail = true;
                        break;
                    }

                    try {
                        result =
                                chain.tryToConnectAndFetchSummary(
                                        block, System.currentTimeMillis() / THOUSAND_MS, false);
                    } catch (Throwable t) {
                        // we want to see the exception and the block where it occurred
                        t.printStackTrace();
                        if (t.getMessage() != null
                                && t.getMessage().contains("Invalid Trie state, missing node ")) {
                            System.out.println(
                                    "The exception above is likely due to a pruned database and NOT a consensus problem.\n"
                                            + "Rebuild the full state by editing the config.xml file or running ./aion.sh --state FULL.\n");
                        }
                        result =
                                new Pair<>() {
                                    @Override
                                    public AionBlockSummary setValue(AionBlockSummary value) {
                                        return null;
                                    }

                                    @Override
                                    public ImportResult getLeft() {
                                        return ImportResult.INVALID_BLOCK;
                                    }

                                    @Override
                                    public AionBlockSummary getRight() {
                                        return null;
                                    }
                                };

                        fail = true;
                    }

                    if (!result.getLeft().isSuccessful()) {
                        System.out.println("Consensus break at block:\n" + block);
                        System.out.println(
                                "Import attempt returned result "
                                        + result.getLeft()
                                        + " with summary\n"
                                        + result.getRight());

                        if (repo.isValidRoot(store.getBestBlock().getStateRoot())) {
                            System.out.println("The repository state trie was:\n");
                            System.out.println(repo.getTrieDump());
                        }

                        fail = true;
                        break;
                    }

                    if (currentBlock % stepSize == 0) {
                        double time = System.currentTimeMillis() - start;

                        double timePerBlock = time / (currentBlock - startHeight + 1);
                        long remainingBlocks = topBlockNumber - currentBlock;
                        double estimate =
                                (timePerBlock * remainingBlocks) / 60_000 + 1; // in minutes
                        System.out.println(
                                "Finished with blocks up to "
                                        + currentBlock
                                        + " in "
                                        + String.format("%.0f", time)
                                        + " ms (under "
                                        + String.format("%.0f", time / 60_000 + 1)
                                        + " min). The average time per block is < "
                                        + String.format("%.0f", timePerBlock + 1)
                                        + " ms. Completion for remaining "
                                        + remainingBlocks
                                        + " blocks estimated to take "
                                        + String.format("%.0f", estimate)
                                        + " min.");
                    }

                    currentBlock++;
                }
            }

            if (fail) {
                System.out.println("Importing stored blocks FAILED.");
            } else {
                System.out.println("Importing stored blocks SUCCESSFUL.");
            }
        } else {
            if (block == null) {
                System.out.println(
                        "The best known block in null. The given database is likely empty. Nothing to do.");
            } else {
                System.out.println(
                        "The given height "
                                + startHeight
                                + " is above the best known block "
                                + block.getNumber()
                                + ". Nothing to do.");
            }
        }

        System.out.println("Closing databases...");
        repo.close();

        System.out.println("Importing stored blocks COMPLETE.");
    }
}
