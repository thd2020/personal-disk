package com.micro.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.micro.chain.core.Bootstrap;
import com.micro.chain.core.HandlerInitializer;
import com.micro.chain.core.Pipeline;
import com.micro.chain.handler.ChunkFileSuffixHandler;
import com.micro.chain.handler.ChunkRedisHandler;
import com.micro.chain.handler.ChunkStoreHandler;
import com.micro.chain.handler.ChunkValidateHandler;
import com.micro.chain.handler.CopyCapacityEnoughHandler;
import com.micro.chain.handler.CopyCapacityUpdateHandler;
import com.micro.chain.handler.CopyGetDataHandler;
import com.micro.chain.handler.CopySaveHandler;
import com.micro.chain.handler.CopySolrHandler;
import com.micro.chain.handler.CopyValidateHandler;
import com.micro.chain.handler.CreateCapacityEnoughHandler;
import com.micro.chain.handler.CreateCapacityUpdateHandler;
import com.micro.chain.handler.CreateExistHandler;
import com.micro.chain.handler.CreateFileCutHandler;
import com.micro.chain.handler.CreateSaveDiskFileHandler;
import com.micro.chain.handler.CreateSaveDiskMd5ChunkHandler;
import com.micro.chain.handler.CreateSaveDiskMd5Handler;
import com.micro.chain.handler.CreateSolrHandler;
import com.micro.chain.handler.CreateValidateHandler;
import com.micro.chain.handler.DiskCapacityUpdateHandler;
import com.micro.chain.handler.DiskSaveDiskFileEditHandler;
import com.micro.chain.handler.EditCapacityEnoughHandler;
import com.micro.chain.handler.EditExistHandler;
import com.micro.chain.handler.EditFileCutHandler;
import com.micro.chain.handler.EditSaveDiskFileHandler;
import com.micro.chain.handler.EditSaveDiskMd5ChunkHandler;
import com.micro.chain.handler.EditSaveDiskMd5Handler;
import com.micro.chain.handler.EditSolrHandler;
import com.micro.chain.handler.EditValidateHandler;
import com.micro.chain.handler.FileDelCapacityHandler;
import com.micro.chain.handler.FileDelDelHandler;
import com.micro.chain.handler.FileDelGetDataHandler;
import com.micro.chain.handler.FileDelRedisHandler;
import com.micro.chain.handler.FileDelSolrHandler;
import com.micro.chain.handler.FileDelValidateHandler;
import com.micro.chain.handler.FolderSaveHandler;
import com.micro.chain.handler.FolderValidateHandler;
import com.micro.chain.handler.FolerSolrHandler;
import com.micro.chain.handler.MergeCapacityIsEnoughHandler;
import com.micro.chain.handler.MergeCapacityUpdateHandler;
import com.micro.chain.handler.MergeCreateFolderHandler;
import com.micro.chain.handler.MergeFileIsBreakHandler;
import com.micro.chain.handler.MergeFileIsExistHandler;
import com.micro.chain.handler.MergeGetChunkHandler;
import com.micro.chain.handler.MergeGlThumnailHandler;
import com.micro.chain.handler.MergeSaveDiskFileHandler;
import com.micro.chain.handler.MergeSaveDiskMd5ChunkHandler;
import com.micro.chain.handler.MergeSaveDiskMd5Handler;
import com.micro.chain.handler.MergeSolrHandler;
import com.micro.chain.handler.MergeSpecialDealHandler;
import com.micro.chain.handler.MergeValidateHander;
import com.micro.chain.handler.MoveSolrHandler;
import com.micro.chain.handler.MoveUpdateHandler;
import com.micro.chain.handler.MoveValidateHandler;
import com.micro.chain.handler.RenameIsExistHandler;
import com.micro.chain.handler.RenameSolrHandler;
import com.micro.chain.handler.RenameUpdateHandler;
import com.micro.chain.handler.RenameValidateHandler;
import com.micro.chain.handler.ShareSaveCapacityEnoughHandler;
import com.micro.chain.handler.ShareSaveCapacityUpdateHandler;
import com.micro.chain.handler.ShareSaveEffectHandler;
import com.micro.chain.handler.ShareSaveGetDataHandler;
import com.micro.chain.handler.ShareSaveInsertHandler;
import com.micro.chain.handler.ShareSaveLogHandler;
import com.micro.chain.handler.ShareSaveSolrHandler;
import com.micro.chain.handler.ShareSaveValidateHandler;
import com.micro.chain.param.ChunkRequest;
import com.micro.chain.param.CopyRequest;
import com.micro.chain.param.CreateRequest;
import com.micro.chain.param.CreateResponse;
import com.micro.chain.param.EditRequest;
import com.micro.chain.param.EditResponse;
import com.micro.chain.param.FileDelRequest;
import com.micro.chain.param.FolderRequest;
import com.micro.chain.param.MergeRequest;
import com.micro.chain.param.MoveRequest;
import com.micro.chain.param.RenameRequest;
import com.micro.chain.param.ShareSaveRequest;
import com.micro.common.CapacityUtils;
import com.micro.common.DateUtils;
import com.micro.common.IconConstant;
import com.micro.common.ValidateUtils;
import com.micro.db.dao.DiskFileDao;
import com.micro.db.dao.DiskMd5Dao;
import com.micro.db.jdbc.DiskFileJdbc;
import com.micro.disk.bean.Chunk;
import com.micro.disk.bean.FileBean;
import com.micro.disk.bean.FileListBean;
import com.micro.disk.bean.FolderProp;
import com.micro.disk.bean.FolderTree;
import com.micro.disk.bean.MergeFileBean;
import com.micro.disk.bean.PageInfo;
import com.micro.disk.service.FileService;
import com.micro.lock.LockContext;
import com.micro.model.DiskFile;
import com.micro.model.DiskMd5;
import com.micro.utils.SpringContentUtils;

@Service(interfaceClass=FileService.class,timeout=120000)//??????1s=1000?????????1??????=60s=60*1000??????
@Component
@Transactional
public class FileServiceImpl implements FileService{
	@Autowired
	private DiskFileDao diskFileDao;
	@Autowired
	private DiskFileJdbc diskFileJdbc;
	@Autowired
	private DiskMd5Dao diskMd5Dao;
	@Autowired
	private SpringContentUtils springContentUtils;
	
	@NacosValue(value="${locktype}",autoRefreshed=true)
    private String locktype;
	
	@NacosValue(value="${lockhost}",autoRefreshed=true)
	private String lockhost;
	
	@Override
	public PageInfo<FileListBean> findPageList(Integer page, Integer limit, String userid, String pid, String typecode,String orderfield,String ordertype) {
		return diskFileJdbc.findAllList(page, limit, userid, pid, typecode,orderfield,ordertype);
	}
	
	@Override
	public PageInfo<FileListBean> findPageListCard(Integer page, Integer limit, String userid, String pid, String typecode,String orderfield,String ordertype) {
		return diskFileJdbc.findAllListCard(page, limit, userid, pid,orderfield,ordertype);
	}
	
	@Override
	public PageInfo<FileListBean> findSpecialList(Integer page, Integer limit, String userid, String typecode,String filesuffix,String filename,String showtype,String orderfield,String ordertype) {
		return diskFileJdbc.findSpecialList(page, limit, userid, typecode,filesuffix,filename, showtype,orderfield,ordertype);
	}

	@Override
	public FileBean findOne(String id) {
		DiskFile file=diskFileDao.findOne(id);
		FileBean bean=new FileBean();
		BeanUtils.copyProperties(file, bean);
		bean.setCreatetime(DateUtils.formatDate(file.getCreatetime(), "yyyy-MM-dd HH:mm:ss"));
		bean.setFilesize(CapacityUtils.convert(file.getFilesize()));
		return bean;
	}
	@Override
	public List<FileBean> findChildrenFiles(String userid,String pid){
		List<DiskFile> files=diskFileDao.findListByPid(userid,pid);
		List<FileBean> beans=new ArrayList<FileBean>();
		if(!CollectionUtils.isEmpty(files)){
			files.forEach(file->{
				FileBean bean=new FileBean();
				BeanUtils.copyProperties(file, bean);
				bean.setCreatetime(DateUtils.formatDate(file.getCreatetime(), "yyyy-MM-dd HH:mm:ss"));
				bean.setFilesize(CapacityUtils.convert(file.getFilesize()));
				beans.add(bean);
			});
		}
		return beans;
	}
	
	@Override
	public void uploadChunk(Chunk chunk) {
		ChunkRequest request=new ChunkRequest();
		BeanUtils.copyProperties(chunk, request);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(ChunkValidateHandler.class));
				//2.?????????????????????????????????
				pipeline.addLast(springContentUtils.getHandler(ChunkFileSuffixHandler.class));
				//3.????????????
				pipeline.addLast(springContentUtils.getHandler(ChunkStoreHandler.class));
				//4.??????????????????Redis
				pipeline.addLast(springContentUtils.getHandler(ChunkRedisHandler.class));				
			}
		});
		bootstrap.execute();
	}
	
	@Override
	public Integer checkFile(String filemd5) {
		String lockname=filemd5;
		try{
			//lockContext.getLock(lockname);
			DiskMd5 diskMd5=diskMd5Dao.findMd5IsExist(filemd5);
			return diskMd5==null?0:1;
		}catch(Exception e){
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}finally{
			//lockContext.unLock(lockname);
		}
	}
	
	@Deprecated
	@Override
	public Integer checkChunk(String userid,String filemd5, Integer chunkNumber) {
		try{
			return null;
		}catch(Exception e){
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/**
	 * ????????????
	 * 1???????????????????????????lockname=${filemd5}
	 * 2??????????????????????????????lockname=${userid}-${folderid}-${rootname}
	 * ????????????????????????????????????bug
	 * bug1???
	 * 		* ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
	 * 		* ?????????????????????????????????????????????
	 * 
	 * bug2???
	 * 		* ???????????????????????????????????????????????????1.png?????????????????????1.png???????????????????????????????????????
	 * 		* ?????????????????????????????????disk_md5??????md5??????????????????????????????????????????????????????????????????????????????????????????????????????
	 */
	@Override
	public void mergeChunk(MergeFileBean bean) {
		String lockname=bean.getFilemd5();
		LockContext lockContext=new LockContext(locktype,lockhost);
		try{
			if(!StringUtils.isEmpty(bean.getRelativepath())){
				String[] names=bean.getRelativepath().split("/");
				String userid=bean.getUserid();
				String folderid=bean.getPid();
				
				lockname="CREATEFOLDER-"+userid+"-"+folderid+"-"+names[0];
			}
			
			//????????????
			lockContext.getLock(lockname);
			
			MergeRequest request=new MergeRequest();
			BeanUtils.copyProperties(bean, request);
			
			Bootstrap bootstrap=new Bootstrap();
			bootstrap.childHandler(new HandlerInitializer(request,null) {
				@Override
				protected void initChannel(Pipeline pipeline) {
					//1.?????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeValidateHander.class));
					//2.????????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeCapacityIsEnoughHandler.class));
					//3.???Redis??????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeGetChunkHandler.class));
					//4.????????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeFileIsBreakHandler.class));
					//5.???????????????????????????md5?????????
					pipeline.addLast(springContentUtils.getHandler(MergeFileIsExistHandler.class));					
					//6.??????disk_md5???
					pipeline.addLast(springContentUtils.getHandler(MergeSaveDiskMd5Handler.class));										
					//7.??????disk_chunk???
					pipeline.addLast(springContentUtils.getHandler(MergeSaveDiskMd5ChunkHandler.class));
					//8.????????????????????????????????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeCreateFolderHandler.class));
					//9.??????disk_file???
					pipeline.addLast(springContentUtils.getHandler(MergeSaveDiskFileHandler.class));
					//10.???????????????????????????????????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeSpecialDealHandler.class));					
					//11.?????????????????????????????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeGlThumnailHandler.class));
					//12.???????????????????????????
					pipeline.addLast(springContentUtils.getHandler(MergeCapacityUpdateHandler.class));
					//13.??????Solr
					pipeline.addLast(springContentUtils.getHandler(MergeSolrHandler.class));
				}
			});
			bootstrap.execute();
			
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}finally{
			//???????????????
			lockContext.unLock(lockname);
		}
	}
	
	
	@Override
	public void addFolder(String pid, String filename, String userid, String username) {
		FolderRequest request=new FolderRequest();
		request.setPid(pid);
		request.setFilename(filename);
		request.setUserid(userid);
		request.setUsername(username);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(FolderValidateHandler.class));
				//2.??????disk_file
				pipeline.addLast(springContentUtils.getHandler(FolderSaveHandler.class));
				//3.??????Solr
				pipeline.addLast(springContentUtils.getHandler(FolerSolrHandler.class));
				//4.????????????
			}
		});
		bootstrap.execute();
	}

	@Override
	public void rename(String userid,String id, String filename) {
		RenameRequest request=new RenameRequest();
		request.setFilename(filename);
		request.setId(id);
		request.setUserid(userid);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(RenameValidateHandler.class));
				//2.????????????????????????
				pipeline.addLast(springContentUtils.getHandler(RenameIsExistHandler.class));
				//3.??????disk_file
				pipeline.addLast(springContentUtils.getHandler(RenameUpdateHandler.class));
				//4.??????Solr
				pipeline.addLast(springContentUtils.getHandler(RenameSolrHandler.class));
				//5.????????????
			}
		});
		bootstrap.execute();
	}
	
	@Override
	public void delete(String createuserid,String createusername,List<String> ids) {
		FileDelRequest request=new FileDelRequest();
		request.setIds(ids);
		request.setCreateuserid(createuserid);
		request.setCreateusername(createusername);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(FileDelValidateHandler.class));
				//2.????????????????????????????????????
				pipeline.addLast(springContentUtils.getHandler(FileDelGetDataHandler.class));
				//3.?????????????????????disk_file_del?????????
				pipeline.addLast(springContentUtils.getHandler(FileDelDelHandler.class));
				//4.??????Solr
				pipeline.addLast(springContentUtils.getHandler(FileDelSolrHandler.class));
				//5.???key?????????Redis????????????
				pipeline.addLast(springContentUtils.getHandler(FileDelRedisHandler.class));
				//6.???????????????????????????
				pipeline.addLast(springContentUtils.getHandler(FileDelCapacityHandler.class));
				//7.????????????
			}
		});
		bootstrap.execute();
	}
	
	@Deprecated
	@Override
	public List<FolderTree> findFolderTree(String userid,String pid,List<String> ids) {
		ValidateUtils.validate(userid, "??????ID");
		if(StringUtils.isEmpty(pid)){
			pid="0";
		}
		List<DiskFile> files=null;
		if(CollectionUtils.isEmpty(ids)){
			files=diskFileDao.findFolderTree(userid, pid);
		}else{
			files=diskFileJdbc.findFolderTree(userid, pid, ids);
		}
		
		List<FolderTree> trees=new ArrayList<FolderTree>();
		if(!CollectionUtils.isEmpty(files)){
			for(DiskFile file:files){
				FolderTree tree=new FolderTree();
				tree.setId(file.getId());
				tree.setLabel(file.getFilename());
				tree.setLeaf(false);
				tree.setChildren(new ArrayList<FolderTree>());
				trees.add(tree);
			}
		}
		return trees;
	}
	
	@Override
	public List<FileBean> findFolderList(String userid,String pid,List<String> ids){
		List<DiskFile> files=diskFileJdbc.findFolderTree(userid, pid, ids);
		List<FileBean> beans=new ArrayList<FileBean>();
		if(!CollectionUtils.isEmpty(files)){
			for(DiskFile file:files){
				FileBean bean=new FileBean();
				bean.setId(file.getId());
				bean.setPid(file.getPid());
				bean.setFileicon(IconConstant.icon);
				bean.setFilename(file.getFilename());
				bean.setFilesize("-");
				bean.setCreatetime(DateUtils.formatDate(file.getCreatetime(), "yyyy-MM-dd HH:mm:ss"));
				beans.add(bean);
			}
		}
		return beans;
	}
	
	@Override
	public void copyTo(String userid,String username,List<String> ids,String folderid) {
		CopyRequest request=new CopyRequest();
		request.setFolderid(folderid);
		request.setIds(ids);
		request.setUserid(userid);
		request.setUsername(username);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(CopyValidateHandler.class));
				//2.??????????????????
				pipeline.addLast(springContentUtils.getHandler(CopyGetDataHandler.class));
				//3.????????????????????????
				pipeline.addLast(springContentUtils.getHandler(CopyCapacityEnoughHandler.class));
				//4.????????????
				pipeline.addLast(springContentUtils.getHandler(CopySaveHandler.class));
				//5.???????????????????????????
				pipeline.addLast(springContentUtils.getHandler(CopyCapacityUpdateHandler.class));
				//6.??????Solr
				pipeline.addLast(springContentUtils.getHandler(CopySolrHandler.class));
				//7.????????????
			}
		});
		bootstrap.execute();
	}
	
	@Override
	public void moveTo(String userid,List<String> ids,String folderid) {
		MoveRequest request=new MoveRequest();
		request.setFolderid(folderid);
		request.setIds(ids);
		request.setUserid(userid);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(MoveValidateHandler.class));
				//2.????????????
				pipeline.addLast(springContentUtils.getHandler(MoveUpdateHandler.class));
				//3.??????Solr
				pipeline.addLast(springContentUtils.getHandler(MoveSolrHandler.class));
				//4.????????????
			}
		});
		bootstrap.execute();
	}
	
	@Override
	public void saveFromShare(String userid,String username, String folderid,String shareid, List<String> ids) {
		ShareSaveRequest request=new ShareSaveRequest();
		shareid=shareid.toLowerCase();
		request.setUserid(userid);
		request.setUsername(username);
		request.setFolderid(folderid);
		request.setShareid(shareid);
		request.setIds(ids);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveValidateHandler.class));
				//2.????????????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveEffectHandler.class));
				//3.??????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveGetDataHandler.class));
				//4.????????????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveCapacityEnoughHandler.class));
				//5.??????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveInsertHandler.class));
				//6.??????Solr
				pipeline.addLast(springContentUtils.getHandler(ShareSaveSolrHandler.class));
				//7.?????????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveCapacityUpdateHandler.class));
				//8.?????????????????????????????????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSaveLogHandler.class));
				//9.????????????
			}
		});
		bootstrap.execute();
	}
	
	@Override
	public List<Map<String, Object>> findParentListByPid(String pid) {
		List<Map<String, Object>> lists=new ArrayList<>();
		if("0".equals(pid)){
			return lists;
		}		
		DiskFile file=diskFileDao.findOne(pid);
		
		Map<String,Object> map=new HashMap<String,Object>();
		map.put("id", pid);
		map.put("name", file.getFilename());
		lists.add(map);
		dgFindParentList(file.getPid(), lists);
		
		return lists;
	}
	
	@Override
	public List<Map<String, Object>> findParentListById(String id) {
		List<Map<String, Object>> lists=new ArrayList<>();
		DiskFile file=diskFileDao.findOne(id);
		if(!"0".equals(file.getPid())){
			dgFindParentList(file.getPid(), lists);
		}
		return lists;
	}

	@Override
	public FolderProp findFolderProp(String userid,String id) {
		FolderProp fp=new FolderProp();
		fp.setFilenum(0);
		fp.setFoldernum(0);
		fp.setTotalsize(0l);
		
		dgFindFolderProp(userid,id, fp);
		
		fp.setTotalsizename(CapacityUtils.convert(fp.getTotalsize()));
		return fp;
	}
	private void dgFindParentList(String pid,List<Map<String, Object>> lists){
		if(!"0".equals(pid)){			
			DiskFile file=diskFileDao.findOne(pid);
			Map<String,Object> map=new HashMap<String,Object>();
			map.put("id", pid);
			map.put("name", file.getFilename());
			lists.add(0, map);
			
			dgFindParentList(file.getPid(), lists);
		}
	}
	private void dgFindFolderProp(String userid,String pid,FolderProp fp){
		List<DiskFile> files=diskFileDao.findListByPid(userid,pid);
		if(!CollectionUtils.isEmpty(files)){
			for(DiskFile file:files){
				fp.setTotalsize(fp.getTotalsize()+file.getFilesize());
				if(file.getFiletype()==1){
					fp.setFilenum(fp.getFilenum()+1);
				}else if(file.getFiletype()==0){
					fp.setFoldernum(fp.getFoldernum()+1);
				}
				dgFindFolderProp(userid,file.getId(), fp);
			}
		}
	}

	@Override
	public FileBean editFile(String fileid, byte[] bytes) {
		String filemd5=DigestUtils.md5DigestAsHex(bytes);
		LockContext lockContext=new LockContext(locktype,lockhost);
		try{
			//?????????????????????md5???????????????????????????
			lockContext.getLock(filemd5);
			
			EditRequest request=new EditRequest();
			request.setBytes(bytes);
			request.setFileid(fileid);
			request.setFilemd5(filemd5);
			
			Bootstrap bootstrap=new Bootstrap();
			bootstrap.childHandler(new HandlerInitializer(request,new EditResponse()) {
				@Override
				protected void initChannel(Pipeline pipeline) {
					//1.????????????
					pipeline.addLast(springContentUtils.getHandler(EditValidateHandler.class));
					//2.????????????????????????
					pipeline.addLast(springContentUtils.getHandler(EditCapacityEnoughHandler.class));
					//3.??????MD5????????????
					pipeline.addLast(springContentUtils.getHandler(EditExistHandler.class));
					//4.????????????????????????FastDFS				
					pipeline.addLast(springContentUtils.getHandler(EditFileCutHandler.class));
					//5.??????disk_md5
					pipeline.addLast(springContentUtils.getHandler(EditSaveDiskMd5Handler.class));
					//6.??????disk_md5_chunk
					pipeline.addLast(springContentUtils.getHandler(EditSaveDiskMd5ChunkHandler.class));
					//7.??????disk_file
					pipeline.addLast(springContentUtils.getHandler(EditSaveDiskFileHandler.class));
					//8.??????disk_file_edit
					pipeline.addLast(springContentUtils.getHandler(DiskSaveDiskFileEditHandler.class));
					//9.??????Solr
					pipeline.addLast(springContentUtils.getHandler(EditSolrHandler.class));
					//10.???????????????????????????
					pipeline.addLast(springContentUtils.getHandler(DiskCapacityUpdateHandler.class));
					//11.????????????
					
				}
			});
			EditResponse res=(EditResponse) bootstrap.execute();
			
			FileBean fb=new FileBean();
			fb.setId(fileid);
			fb.setFilename(res.getFilename());
			fb.setFilemd5(filemd5);
			
			return fb;
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}finally{
			//??????????????????
			lockContext.unLock(filemd5);
		}
	}

	@Override
	public FileBean addFile(String pid,String filename, byte[] bytes, String userid, String username) {
		String filemd5=DigestUtils.md5DigestAsHex(bytes);
		LockContext lockContext=new LockContext(locktype,lockhost);
		try{
			//?????????????????????md5???????????????????????????
			lockContext.getLock(filemd5);
			
			CreateRequest request=new CreateRequest();
			request.setBytes(bytes);
			request.setFilename(filename);
			request.setPid(pid);
			request.setUserid(userid);
			request.setUsername(username);
			request.setFilemd5(filemd5);
			
			Bootstrap bootstrap=new Bootstrap();
			bootstrap.childHandler(new HandlerInitializer(request,new CreateResponse()) {
				@Override
				protected void initChannel(Pipeline pipeline) {
					//1.????????????
					pipeline.addLast(springContentUtils.getHandler(CreateValidateHandler.class));
					//2.????????????????????????
					pipeline.addLast(springContentUtils.getHandler(CreateCapacityEnoughHandler.class));
					//3.??????MD5????????????
					pipeline.addLast(springContentUtils.getHandler(CreateExistHandler.class));
					//4.????????????????????????FastDFS	
					pipeline.addLast(springContentUtils.getHandler(CreateFileCutHandler.class));
					//5.??????disk_md5
					pipeline.addLast(springContentUtils.getHandler(CreateSaveDiskMd5Handler.class));
					//6.??????disk_md5_chunk
					pipeline.addLast(springContentUtils.getHandler(CreateSaveDiskMd5ChunkHandler.class));
					//7.??????disk_file
					pipeline.addLast(springContentUtils.getHandler(CreateSaveDiskFileHandler.class));
					//8.??????Solr
					pipeline.addLast(springContentUtils.getHandler(CreateSolrHandler.class));
					//9.???????????????????????????
					pipeline.addLast(springContentUtils.getHandler(CreateCapacityUpdateHandler.class));
					//10.????????????
				}
			});
			CreateResponse res=(CreateResponse) bootstrap.execute();
			
			FileBean fb=new FileBean();
			fb.setId(res.getFileid());
			fb.setFilename(filename);
			fb.setFilemd5(filemd5);
			return fb;
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}finally{
			//??????????????????
			lockContext.unLock(filemd5);
		}	
	}
	
}
