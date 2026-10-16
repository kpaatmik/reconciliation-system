package com.kpaatmik.reconciliation.reader;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;

@Configuration
public class BaselineReaderConfig {

	@Bean
	@StepScope
	public FlatFileItemReader<BaselineCsvRecord> baselineReader(ReconciliationFileResolver fileResolver) {

		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setDelimiter(",");
		tokenizer.setQuoteCharacter('"');
		tokenizer.setNames("transaction_id", "account_number", "transaction_date", "amount", "currency",
				"transaction_type", "description");

		DefaultLineMapper<BaselineCsvRecord> lineMapper = new DefaultLineMapper<>() {
			@Override
			public BaselineCsvRecord mapLine(String line, int lineNumber) throws FlatFileParseException {

				try {
					var fieldSet = tokenizer.tokenize(line);

					return new BaselineCsvRecord(lineNumber, fieldSet.readString("transaction_id"),
							fieldSet.readString("account_number"), fieldSet.readString("transaction_date"),
							fieldSet.readString("amount"), fieldSet.readString("currency"),
							fieldSet.readString("transaction_type"), fieldSet.readString("description"));
				} catch (Exception ex) {
					throw new FlatFileParseException("Unable to parse baseline CSV row at line " + lineNumber, ex, line,
							lineNumber);
				}
			}
		};

		return new FlatFileItemReaderBuilder<BaselineCsvRecord>().name("baselineReader")
				.resource(new FileSystemResource(fileResolver.getBaselineFile())).linesToSkip(1).lineMapper(lineMapper)
				.saveState(true).strict(true).build();
	}
}
